#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('eventpass-lab-test-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path "$tempRoot/infra/aws", "$tempRoot/infra/database", "$tempRoot/.local" -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'initialize-lab.ps1') -Destination "$tempRoot/infra/aws/initialize-lab.ps1"
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../database/tickets-table.json') -Destination "$tempRoot/infra/database/tickets-table.json"
$fakeScript = Join-Path $tempRoot 'fake-aws.ps1'
@'
param([string[]] $AwsArguments, [string] $InputFile)
Invoke-FakeAws -Arguments $AwsArguments -InputFile $InputFile | ConvertTo-Json -Depth 30 -Compress
'@ | Set-Content -LiteralPath $fakeScript

function Assert-True($Condition, $Message) {
    if (-not $Condition) { throw $Message }
}
function global:Invoke-FakeAws([string[]] $Arguments, [string] $InputFile) {
    $operation = $Arguments[0..1] -join ' '
    $global:labCalls.Add($operation)
    switch ($operation) {
        'sts get-caller-identity' { return @{ Account = $global:labAccount } }
        'iam get-role' { return @{ Role = @{ Arn = 'arn:aws:iam::119065066402:role/LabRole' } } }
        'ecr describe-repositories' { return @{ repositories = @($global:labRepos | ForEach-Object { @{ repositoryName = $_ } }) } }
        'ecr create-repository' { $global:labRepos.Add($Arguments[$Arguments.IndexOf('--repository-name') + 1]); return @{} }
        'logs describe-log-groups' { return @{ logGroups = @($global:labLogs | ForEach-Object { @{ logGroupName = $_ } }) } }
        'logs create-log-group' { $global:labLogs.Add($Arguments[$Arguments.IndexOf('--log-group-name') + 1]); return @{} }
        'logs put-retention-policy' { return @{} }
        'sqs list-queues' {
            if ($global:labQueues.Count -eq 0) { return @{} }
            return @{ QueueUrls = @($global:labQueues) }
        }
        'sqs create-queue' {
            $name = $Arguments[$Arguments.IndexOf('--queue-name') + 1]
            $url = 'https://sqs.us-east-1.amazonaws.com/119065066402/' + $name
            $global:labQueues.Add($url)
            return @{ QueueUrl = $url }
        }
        'sqs set-queue-attributes' {
            $request = Get-Content -Raw -LiteralPath $InputFile | ConvertFrom-Json
            if ($request.Attributes.RedrivePolicy) {
                $policy = $request.Attributes.RedrivePolicy | ConvertFrom-Json
                Assert-True ($policy.maxReceiveCount -eq '5') 'Debe permitir cinco intentos.'
                Assert-True ($policy.deadLetterTargetArn.EndsWith(':eventpass-purchase-errors')) 'DLQ incorrecta.'
                Assert-True ($request.Attributes.VisibilityTimeout -eq '120') 'Visibilidad incorrecta.'
            }
            return @{}
        }
        'dynamodb list-tables' { return @{ TableNames = $(if ($global:labTable) { @('eventpass-tickets') } else { @() }) } }
        'dynamodb create-table' {
            $request = Get-Content -Raw -LiteralPath $InputFile | ConvertFrom-Json
            Assert-True ($request.TableName -eq 'eventpass-tickets') 'Tabla incorrecta.'
            $global:labTable = $true
            return @{}
        }
        'dynamodb wait' { return @{} }
        'dynamodb describe-table' { return @{ Table = @{ KeySchema = @(@{ AttributeName = 'pk' }); GlobalSecondaryIndexes = @(@{ IndexName = 'by-user' }) } } }
        'ecs describe-clusters' { return @{ clusters = $(if ($global:labCluster) { @(@{ status = 'ACTIVE'; clusterArn = 'cluster-arn' }) } else { @() }) } }
        'ecs create-cluster' { $global:labCluster = $true; return @{ cluster = @{ clusterArn = 'cluster-arn' } } }
        default { throw "Operación inesperada: $operation" }
    }
}
try {
    $global:labCalls = [Collections.Generic.List[string]]::new()
    $global:labRepos = [Collections.Generic.List[string]]::new()
    $global:labLogs = [Collections.Generic.List[string]]::new()
    $global:labQueues = [Collections.Generic.List[string]]::new()
    $global:labAccount = '000000000000'
    $global:labTable = $false
    $global:labCluster = $false
    $rejected = $false
    try { & "$tempRoot/infra/aws/initialize-lab.ps1" -AwsScript $fakeScript | Out-Null }
    catch { $rejected = $_.Exception.Message -like '*otra cuenta*' }
    Assert-True $rejected 'Debe rechazar credenciales de otra cuenta.'
    Assert-True ($labCalls.Count -eq 1) 'No debe crear recursos en otra cuenta.'

    $global:labAccount = '119065066402'
    $global:labCalls.Clear()
    & "$tempRoot/infra/aws/initialize-lab.ps1" -AwsScript $fakeScript -WhatIf | Out-Null
    Assert-True (-not ($labCalls | Where-Object { $_ -match 'create|set-|put-' })) 'WhatIf no debe escribir en AWS.'
    & "$tempRoot/infra/aws/initialize-lab.ps1" -AwsScript $fakeScript | Out-Null
    Assert-True ($labRepos.Count -eq 5 -and 'eventpass/purchase-lambda' -in $labRepos -and $labQueues.Count -eq 5 -and $labLogs.Count -eq 4 -and $labTable -and $labCluster) 'Faltan recursos de base, incluido el repositorio Docker de Lambda.'
    $global:labCalls.Clear()
    & "$tempRoot/infra/aws/initialize-lab.ps1" -AwsScript $fakeScript | Out-Null
    Assert-True (-not ($labCalls | Where-Object { $_ -match 'create-' })) 'Repetir no debe duplicar recursos.'
    Write-Host 'OK: cuenta incorrecta, WhatIf, recursos, DLQ e idempotencia.'
} finally {
    $resolvedTest = [IO.Path]::GetFullPath($tempRoot)
    $allowedRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedTest.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase) -or
        -not ([IO.Path]::GetFileName($resolvedTest)).StartsWith('eventpass-lab-test-')) {
        throw 'Se rechazó limpiar un directorio fuera del temporal de pruebas.'
    }
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
    Remove-Item Function:/Invoke-FakeAws -ErrorAction SilentlyContinue
    foreach ($name in @('labCalls','labRepos','labLogs','labQueues','labAccount','labTable','labCluster')) {
        Remove-Variable -Name $name -Scope Global -ErrorAction SilentlyContinue
    }
}
