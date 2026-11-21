#Requires -Version 7.0
[CmdletBinding(SupportsShouldProcess)]
param(
    [string] $AwsScript = (Join-Path $PSScriptRoot 'Invoke-LabAws.ps1')
)

$ErrorActionPreference = 'Stop'
function Invoke-AwsJson([string[]] $Arguments, [string] $InputFile) {
    $output = & $awsScript -AwsArguments ($Arguments + @('--output', 'json')) -InputFile $InputFile
    if ($output) { return ($output | ConvertFrom-Json -Depth 40) }
}
$account = '119065066402'
$region = 'us-east-1'
$identity = Invoke-AwsJson -Arguments @('sts', 'get-caller-identity')
if ($identity.Account -ne $account) { throw 'La sesión pertenece a otra cuenta. No se crearon recursos.' }
$role = Invoke-AwsJson -Arguments @('iam', 'get-role', '--role-name', 'LabRole')
if ($role.Role.Arn -ne "arn:aws:iam::${account}:role/LabRole") { throw 'No se pudo confirmar LabRole.' }

$serviceNames = @('ms-auth', 'ms-events', 'ms-orders', 'ms-tickets')
$repositoryNames = @($serviceNames) + @('purchase-lambda')
$repositories = Invoke-AwsJson -Arguments @('ecr', 'describe-repositories')
foreach ($service in $repositoryNames) {
    $name = "eventpass/$service"
    if ($name -notin $repositories.repositories.repositoryName) {
        if ($PSCmdlet.ShouldProcess($name, 'Crear repositorio ECR privado e inmutable')) {
            Invoke-AwsJson -Arguments @('ecr', 'create-repository', '--repository-name', $name,
                '--image-tag-mutability', 'IMMUTABLE', '--encryption-configuration', 'encryptionType=AES256') | Out-Null
        }
    }
}

$existingLogs = Invoke-AwsJson -Arguments @('logs', 'describe-log-groups', '--log-group-name-prefix', '/ecs/eventpass/')
foreach ($service in $serviceNames) {
    $name = "/ecs/eventpass/$service"
    if ($PSCmdlet.ShouldProcess($name, 'Preparar logs con retención de siete días')) {
        if ($name -notin $existingLogs.logGroups.logGroupName) {
            Invoke-AwsJson -Arguments @('logs', 'create-log-group', '--log-group-name', $name) | Out-Null
        }
        Invoke-AwsJson -Arguments @('logs', 'put-retention-policy', '--log-group-name', $name, '--retention-in-days', '7') | Out-Null
    }
}

$queueNames = @('eventpass-purchase-errors', 'eventpass-purchase-pending',
    'eventpass-events-published-queue', 'eventpass-events-cancelled-queue', 'eventpass-orders-completed-queue')
$queueUrls = @{}
$existingQueues = Invoke-AwsJson -Arguments @('sqs', 'list-queues', '--queue-name-prefix', 'eventpass-')
foreach ($name in $queueNames) {
    $url = $existingQueues.QueueUrls | Where-Object { $_ -and $_.EndsWith('/' + $name) } | Select-Object -First 1
    if (-not $url -and $PSCmdlet.ShouldProcess($name, 'Crear cola SQS cifrada')) {
        $result = Invoke-AwsJson -Arguments @('sqs', 'create-queue', '--queue-name', $name,
            '--attributes', 'SqsManagedSseEnabled=true')
        $url = $result.QueueUrl
    }
    if ($url) { $queueUrls[$name] = $url }
}
if ($queueUrls['eventpass-purchase-errors'] -and $queueUrls['eventpass-purchase-pending']) {
    $errorArn = "arn:aws:sqs:${region}:${account}:eventpass-purchase-errors"
    $sourceArn = "arn:aws:sqs:${region}:${account}:eventpass-purchase-pending"
    $settings = @{
        'eventpass-purchase-errors' = @{
            MessageRetentionPeriod = '1209600'
            RedriveAllowPolicy = (@{ redrivePermission = 'byQueue'; sourceQueueArns = @($sourceArn) } | ConvertTo-Json -Compress)
        }
        'eventpass-purchase-pending' = @{
            VisibilityTimeout = '120'
            RedrivePolicy = (@{ deadLetterTargetArn = $errorArn; maxReceiveCount = '5' } | ConvertTo-Json -Compress)
        }
    }
    foreach ($name in $settings.Keys) {
        if ($PSCmdlet.ShouldProcess($name, 'Configurar reintentos y recuperación de compras')) {
            $request = @{ QueueUrl = $queueUrls[$name]; Attributes = $settings[$name] } | ConvertTo-Json -Depth 8
            $tempFile = Join-Path ([IO.Path]::GetTempPath()) (([guid]::NewGuid().ToString()) + '.json')
            try {
                [IO.File]::WriteAllText($tempFile, $request)
                Invoke-AwsJson -Arguments @('sqs', 'set-queue-attributes', '--cli-input-json', 'file:///input/request.json') -InputFile $tempFile | Out-Null
            } finally { Remove-Item -LiteralPath $tempFile -ErrorAction SilentlyContinue }
        }
    }
}

$tables = Invoke-AwsJson -Arguments @('dynamodb', 'list-tables')
if ('eventpass-tickets' -notin $tables.TableNames -and $PSCmdlet.ShouldProcess('eventpass-tickets', 'Crear tabla DynamoDB de tickets')) {
    $tableFile = Join-Path $PSScriptRoot '../database/tickets-table.json'
    Invoke-AwsJson -Arguments @('dynamodb', 'create-table', '--cli-input-json', 'file:///input/request.json') -InputFile $tableFile | Out-Null
}
if (-not $WhatIfPreference) {
    Invoke-AwsJson -Arguments @('dynamodb', 'wait', 'table-exists', '--table-name', 'eventpass-tickets') | Out-Null
    $table = (Invoke-AwsJson -Arguments @('dynamodb', 'describe-table', '--table-name', 'eventpass-tickets')).Table
    if ($table.KeySchema.Count -ne 1 -or $table.KeySchema[0].AttributeName -ne 'pk' -or
        'by-user' -notin $table.GlobalSecondaryIndexes.IndexName) {
        throw 'La tabla existente no corresponde al esquema de Tickets. No se modificó su esquema.'
    }
}

$clusters = Invoke-AwsJson -Arguments @('ecs', 'describe-clusters', '--clusters', 'eventpass')
$cluster = $clusters.clusters | Where-Object { $_.status -eq 'ACTIVE' }
if (-not $cluster -and $PSCmdlet.ShouldProcess('eventpass', 'Crear clúster ECS para Fargate sin tareas activas')) {
    $result = Invoke-AwsJson -Arguments @('ecs', 'create-cluster', '--cluster-name', 'eventpass', '--capacity-providers', 'FARGATE')
    $cluster = $result.cluster
}

# Inventory has no credentials and is kept outside Git to reflect this lab session.
$inventory = [ordered]@{
    AccountId = $account; Region = $region; TaskRoleArn = $role.Role.Arn
    ClusterArn = $cluster.clusterArn; Repositories = @($repositoryNames | ForEach-Object {
        "${account}.dkr.ecr.${region}.amazonaws.com/eventpass/$_"
    }); Queues = $queueUrls; TicketsTable = 'eventpass-tickets'
}
if (-not $WhatIfPreference) {
    $inventory | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $PSScriptRoot '../../.local/aws-lab-resources.json')
}
$inventory
