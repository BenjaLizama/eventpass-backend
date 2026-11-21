#Requires -Version 7.0
[CmdletBinding()]
param([switch] $Apply)

$ErrorActionPreference = 'Stop'
$awsScript = Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'
function Invoke-DataAws([string[]] $Arguments, [string] $InputFile) {
    $output = & $awsScript -AwsArguments ($Arguments + @('--output', 'json')) -InputFile $InputFile
    if ($output) { $output | ConvertFrom-Json -Depth 60 }
}
$identity = Invoke-DataAws -Arguments @('sts', 'get-caller-identity')
if ($identity.Account -ne '119065066402') { throw 'Cuenta incorrecta. No se prepararon bases ni secretos.' }
$network = (Invoke-DataAws -Arguments @('cloudformation', 'describe-stacks', '--stack-name', 'eventpass-network')).Stacks[0]
if ($network.StackStatus -notin @('CREATE_COMPLETE', 'UPDATE_COMPLETE')) { throw 'La red debe estar completa.' }
$ids = @{}; foreach ($item in $network.Outputs) { $ids[$item.OutputKey] = $item.OutputValue }
$local = Join-Path $PSScriptRoot '../../.local'
New-Item -ItemType Directory -Path $local -Force | Out-Null
$template = Join-Path $local 'data-template.json'
& node (Join-Path $PSScriptRoot 'data-template.mjs') $template
if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar la plantilla de datos.' }
Invoke-DataAws -Arguments @('cloudformation', 'validate-template', '--template-body', 'file:///input/request.json') -InputFile $template | Out-Null
$stacks = Invoke-DataAws -Arguments @('cloudformation', 'list-stacks', '--query', 'StackSummaries[].{Name:StackName,Status:StackStatus}')
$stack = $stacks | Where-Object { $_.Name -eq 'eventpass-data' -and $_.Status -ne 'DELETE_COMPLETE' } | Select-Object -First 1
if ($stack -and $stack.Status -notin @('CREATE_COMPLETE','UPDATE_COMPLETE','REVIEW_IN_PROGRESS')) {
    throw "El stack de datos está en $($stack.Status). Revisa los eventos; no se reemplazó ni borró ningún recurso."
}
$type = if (-not $stack -or $stack.Status -eq 'REVIEW_IN_PROGRESS') { 'CREATE' } else { 'UPDATE' }
$change = Invoke-DataAws -Arguments @('cloudformation', 'create-change-set', '--stack-name', 'eventpass-data',
    '--change-set-name', ('eventpass-' + [guid]::NewGuid().ToString('N')), '--change-set-type', $type,
    '--template-body', 'file:///input/request.json', '--parameters',
    "ParameterKey=DatabaseSubnetGroup,ParameterValue=$($ids.DatabaseSubnets)",
    "ParameterKey=CacheSubnetGroup,ParameterValue=$($ids.CacheSubnets)",
    "ParameterKey=DataSecurityGroup,ParameterValue=$($ids.DataSecurityGroup)",
    '--tags', 'Key=Project,Value=eventpass') -InputFile $template
$hasChanges = $true
try {
    Invoke-DataAws -Arguments @('cloudformation', 'wait', 'change-set-create-complete', '--change-set-name', $change.Id) | Out-Null
} catch {
    $detail = Invoke-DataAws -Arguments @('cloudformation', 'describe-change-set', '--change-set-name', $change.Id)
    if ($detail.StatusReason -like "*didn't contain changes*" -or $detail.StatusReason -like '*No updates are to be performed*') { $hasChanges = $false }
    else { throw "No se pudo preparar el cambio: $($detail.StatusReason)" }
}
if ($hasChanges) {
    $detail = Invoke-DataAws -Arguments @('cloudformation', 'describe-change-set', '--change-set-name', $change.Id)
    $summary = $detail.Changes | ForEach-Object { [pscustomobject]@{ Action = $_.ResourceChange.Action;
        Resource = $_.ResourceChange.LogicalResourceId; Replacement = $_.ResourceChange.Replacement } }
    $summary | Format-Table | Out-Host
    if ($summary | Where-Object { $_.Action -eq 'Remove' -or ($_.Replacement -and $_.Replacement -ne 'False') }) {
        throw 'El plan elimina o reemplaza recursos de datos. No se ejecutó.'
    }
    if (-not $Apply) { Write-Host "Plan: $($change.Id)"; return }
    Invoke-DataAws -Arguments @('cloudformation', 'execute-change-set', '--change-set-name', $change.Id) | Out-Null
    $waiter = if ($type -eq 'CREATE') { 'stack-create-complete' } else { 'stack-update-complete' }
    Invoke-DataAws -Arguments @('cloudformation', 'wait', $waiter, '--stack-name', 'eventpass-data') | Out-Null
}
$outputs = Invoke-DataAws -Arguments @('cloudformation', 'describe-stacks', '--stack-name', 'eventpass-data', '--query', 'Stacks[0].Outputs')
$outputs | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $local 'aws-data-outputs.json')
$network.Outputs | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $local 'aws-network-outputs.json')
& node (Join-Path $PSScriptRoot 'task-settings.mjs') (Join-Path $local 'aws-network-outputs.json') (Join-Path $local 'aws-data-outputs.json') (Join-Path $local 'aws-task-settings.json')
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron generar las variables de tareas.' }
Write-Host 'Recursos de datos disponibles. Se guardaron endpoints y referencias de secretos; faltan las bases lógicas y su prueba desde ECS.'
