#Requires -Version 7.0
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$awsScript = Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'
function Invoke-BootstrapAws([string[]] $Arguments, [string] $InputFile) {
    $output = & $awsScript -AwsArguments ($Arguments + @('--output', 'json')) -InputFile $InputFile
    if ($output) { $output | ConvertFrom-Json -Depth 60 }
}
function Save-BootstrapRequest($Value, [string] $Name) {
    $path = Join-Path $local $Name
    $Value | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $path
    return $path
}
$identity = Invoke-BootstrapAws -Arguments @('sts', 'get-caller-identity')
if ($identity.Account -ne '119065066402') { throw 'Cuenta incorrecta. No se lanzará la tarea.' }
$local = Join-Path $PSScriptRoot '../../.local'
New-Item -ItemType Directory -Path $local -Force | Out-Null
$ids = @{}
foreach ($name in @('eventpass-network','eventpass-data')) {
    $stack = (Invoke-BootstrapAws -Arguments @('cloudformation', 'describe-stacks', '--stack-name', $name)).Stacks[0]
    if ($stack.StackStatus -notin @('CREATE_COMPLETE','UPDATE_COMPLETE')) { throw "$name aún no está disponible." }
    $filename = if ($name -eq 'eventpass-network') { 'aws-network-outputs.json' } else { 'aws-data-outputs.json' }
    $stack.Outputs | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $local $filename)
    foreach ($item in $stack.Outputs) { $ids[$item.OutputKey] = $item.OutputValue }
}
$running = Invoke-BootstrapAws -Arguments @('ecs', 'list-tasks', '--cluster', 'eventpass', '--family', 'eventpass-data-bootstrap', '--desired-status', 'RUNNING')
if ($running.taskArns.Count -gt 0) { throw 'Ya hay una tarea de inicialización ejecutándose.' }
$repo = 'eventpass/data-bootstrap'
$repositories = Invoke-BootstrapAws -Arguments @('ecr', 'describe-repositories', '--query', 'repositories[].repositoryName')
if ($repo -notin $repositories) {
    Invoke-BootstrapAws -Arguments @('ecr', 'create-repository', '--repository-name', $repo, '--image-tag-mutability', 'IMMUTABLE') | Out-Null
}
$registry = '119065066402.dkr.ecr.us-east-1.amazonaws.com'
$password = & $awsScript -AwsArguments @('ecr', 'get-login-password')
try {
    $password | & docker login --username AWS --password-stdin $registry
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo autenticar Docker en ECR.' }
} finally { $password = $null }
$tag = 'bootstrap-' + [guid]::NewGuid().ToString('N')
$image = "${registry}/${repo}:$tag"
& docker build --platform linux/amd64 -t $image (Join-Path $PSScriptRoot 'data-bootstrap')
if ($LASTEXITCODE -ne 0) { throw 'No se pudo construir la imagen de inicialización.' }
& docker push $image
if ($LASTEXITCODE -ne 0) { throw 'No se pudo publicar la imagen de inicialización.' }
$info = Invoke-BootstrapAws -Arguments @('ecr', 'describe-images', '--repository-name', $repo, '--image-ids', "imageTag=$tag")
$digest = $info.imageDetails[0].imageDigest
if ($digest -notmatch '^sha256:[a-f0-9]{64}$') { throw 'Digest ECR inválido.' }
$logs = Invoke-BootstrapAws -Arguments @('logs', 'describe-log-groups', '--log-group-name-prefix', '/ecs/eventpass/data-bootstrap')
if ('/ecs/eventpass/data-bootstrap' -notin $logs.logGroups.logGroupName) {
    Invoke-BootstrapAws -Arguments @('logs', 'create-log-group', '--log-group-name', '/ecs/eventpass/data-bootstrap') | Out-Null
}
Invoke-BootstrapAws -Arguments @('logs', 'put-retention-policy', '--log-group-name', '/ecs/eventpass/data-bootstrap', '--retention-in-days', '7') | Out-Null
$role = 'arn:aws:iam::119065066402:role/LabRole'
$definition = @{
    family = 'eventpass-data-bootstrap'; requiresCompatibilities = @('FARGATE'); networkMode = 'awsvpc'; cpu = '256'; memory = '512'
    runtimePlatform = @{ operatingSystemFamily = 'LINUX'; cpuArchitecture = 'X86_64' }
    executionRoleArn = $role; taskRoleArn = $role
    containerDefinitions = @(@{
        name = 'data-bootstrap'; image = "${registry}/${repo}@$digest"; essential = $true
        environment = @(@{ name = 'DATABASE_HOST'; value = $ids.DatabaseEndpoint }, @{ name = 'REDIS_HOST'; value = $ids.RedisEndpoint })
        secrets = @(
            @{ name = 'MASTER_DB_PASSWORD'; valueFrom = $ids.MasterSecretArn + ':password::' },
            @{ name = 'AUTH_DB_PASSWORD'; valueFrom = $ids.AuthDatabaseSecretArn + ':password::' },
            @{ name = 'EVENTS_DB_PASSWORD'; valueFrom = $ids.EventsDatabaseSecretArn + ':password::' },
            @{ name = 'ORDERS_DB_PASSWORD'; valueFrom = $ids.OrdersDatabaseSecretArn + ':password::' },
            @{ name = 'REDIS_PASSWORD'; valueFrom = $ids.RedisSecretArn + ':password::' }
        )
        logConfiguration = @{ logDriver = 'awslogs'; options = @{
            'awslogs-group' = '/ecs/eventpass/data-bootstrap'; 'awslogs-region' = 'us-east-1'; 'awslogs-stream-prefix' = 'bootstrap'
        } }
    })
}
$file = Save-BootstrapRequest $definition 'data-bootstrap-task.json'
$registered = Invoke-BootstrapAws -Arguments @('ecs', 'register-task-definition', '--cli-input-json', 'file:///input/request.json') -InputFile $file
$request = @{
    cluster = 'eventpass'; launchType = 'FARGATE'; platformVersion = 'LATEST'; count = 1
    taskDefinition = $registered.taskDefinition.taskDefinitionArn; clientToken = [guid]::NewGuid().ToString()
    networkConfiguration = @{ awsvpcConfiguration = @{
        subnets = @($ids.AppA, $ids.AppB); securityGroups = @($ids.AppSecurityGroup); assignPublicIp = 'DISABLED'
    } }
}
$file = Save-BootstrapRequest $request 'data-bootstrap-run.json'
$run = Invoke-BootstrapAws -Arguments @('ecs', 'run-task', '--cli-input-json', 'file:///input/request.json') -InputFile $file
if ($run.failures.Count -gt 0 -or $run.tasks.Count -ne 1) { throw 'ECS rechazó la tarea de inicialización.' }
$taskArn = $run.tasks[0].taskArn
Write-Host "Tarea privada de verificación: $taskArn"
Invoke-BootstrapAws -Arguments @('ecs', 'wait', 'tasks-stopped', '--cluster', 'eventpass', '--tasks', $taskArn) | Out-Null
$task = (Invoke-BootstrapAws -Arguments @('ecs', 'describe-tasks', '--cluster', 'eventpass', '--tasks', $taskArn)).tasks[0]
$taskId = ($taskArn -split '/')[-1]
$streamName = "bootstrap/data-bootstrap/$taskId"
$streams = Invoke-BootstrapAws -Arguments @('logs', 'describe-log-streams', '--log-group-name', '/ecs/eventpass/data-bootstrap', '--log-stream-name-prefix', $streamName)
if ($streamName -in $streams.logStreams.logStreamName) {
    $events = Invoke-BootstrapAws -Arguments @('logs', 'get-log-events', '--log-group-name', '/ecs/eventpass/data-bootstrap', '--log-stream-name', $streamName)
    $events.events.message | ForEach-Object { Write-Host $_ }
}
if ($task.containers[0].exitCode -ne 0) { throw "La verificación falló: $($task.stoppedReason). Revisa el código seguro mostrado en los logs." }
& node (Join-Path $PSScriptRoot 'task-settings.mjs') (Join-Path $local 'aws-network-outputs.json') (Join-Path $local 'aws-data-outputs.json') (Join-Path $local 'aws-task-settings.json')
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron generar las variables de tareas.' }
@{ taskArn = $taskArn; imageDigest = $digest; exitCode = 0 } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $local 'aws-data-verification.json')
Write-Host 'Bases SQL, aislamiento y Redis verificados. La tarea temporal ya se detuvo.'
