#Requires -Version 7.0
[CmdletBinding()]
param([switch] $Apply, [string] $AwsScript = (Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'))

$ErrorActionPreference = 'Stop'
function Invoke-NetworkAws([string[]] $Arguments, [string] $InputFile) {
    $output = & $awsScript -AwsArguments ($Arguments + @('--output', 'json')) -InputFile $InputFile
    if ($output) { $output | ConvertFrom-Json -Depth 60 }
}
$identity = Invoke-NetworkAws -Arguments @('sts', 'get-caller-identity')
if ($identity.Account -ne '119065066402') { throw 'Cuenta incorrecta. No se preparó la red.' }
$local = Join-Path $PSScriptRoot '../../.local'
New-Item -ItemType Directory -Path $local -Force | Out-Null
$template = Join-Path $local 'network-template.json'
& node (Join-Path $PSScriptRoot 'network-template.mjs') $template
if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar la plantilla de red.' }
Invoke-NetworkAws -Arguments @('cloudformation', 'validate-template', '--template-body', 'file:///input/request.json') -InputFile $template | Out-Null
$stacks = Invoke-NetworkAws -Arguments @('cloudformation', 'list-stacks', '--query', 'StackSummaries[].{Name:StackName,Status:StackStatus}')
$stack = $stacks | Where-Object { $_.Name -eq 'eventpass-network' -and $_.Status -ne 'DELETE_COMPLETE' } | Select-Object -First 1
if ($stack -and $stack.Status -notin @('CREATE_COMPLETE','UPDATE_COMPLETE','REVIEW_IN_PROGRESS')) {
    throw "El stack está en $($stack.Status). Revisa los eventos antes de intentar otro cambio."
}
$type = if (-not $stack -or $stack.Status -eq 'REVIEW_IN_PROGRESS') { 'CREATE' } else { 'UPDATE' }
$changeName = 'eventpass-' + [guid]::NewGuid().ToString('N')
$authorizer = Invoke-NetworkAws -Arguments @('lambda', 'list-functions', '--query', "Functions[?FunctionName=='eventpass-gateway-authorizer'].FunctionArn | [0]")
$authorizerArn = if ($authorizer) { [string]$authorizer } else { '' }
$change = Invoke-NetworkAws -Arguments @('cloudformation', 'create-change-set', '--stack-name', 'eventpass-network',
    '--change-set-name', $changeName, '--change-set-type', $type, '--template-body', 'file:///input/request.json',
    '--parameters', "ParameterKey=GatewayAuthorizerArn,ParameterValue=$authorizerArn",
    '--tags', 'Key=Project,Value=eventpass') -InputFile $template
try {
    Invoke-NetworkAws -Arguments @('cloudformation', 'wait', 'change-set-create-complete', '--change-set-name', $change.Id) | Out-Null
} catch {
    $detail = Invoke-NetworkAws -Arguments @('cloudformation', 'describe-change-set', '--change-set-name', $change.Id)
    if ($detail.StatusReason -like "*didn't contain changes*" -or $detail.StatusReason -like '*No updates are to be performed*') {
        Write-Host 'La red ya coincide con la plantilla.'
        return
    }
    throw "No se pudo preparar el cambio: $($detail.StatusReason)"
}
$detail = Invoke-NetworkAws -Arguments @('cloudformation', 'describe-change-set', '--change-set-name', $change.Id)
$summary = $detail.Changes | ForEach-Object {
    [pscustomobject]@{ Action = $_.ResourceChange.Action; Resource = $_.ResourceChange.LogicalResourceId;
        Type = $_.ResourceChange.ResourceType; Replacement = $_.ResourceChange.Replacement }
}
$summary | Format-Table | Out-Host
if ($summary | Where-Object { $_.Action -eq 'Remove' -or ($_.Replacement -and $_.Replacement -ne 'False') }) {
    throw 'El plan elimina o reemplaza recursos existentes. No se ejecutó.'
}
Write-Host "Plan de CloudFormation: $($change.Id)"
if (-not $Apply) { return }
Invoke-NetworkAws -Arguments @('cloudformation', 'execute-change-set', '--change-set-name', $change.Id) | Out-Null
$waiter = if ($type -eq 'CREATE') { 'stack-create-complete' } else { 'stack-update-complete' }
Invoke-NetworkAws -Arguments @('cloudformation', 'wait', $waiter, '--stack-name', 'eventpass-network') | Out-Null
$result = Invoke-NetworkAws -Arguments @('cloudformation', 'describe-stacks', '--stack-name', 'eventpass-network', '--query', 'Stacks[0].Outputs')
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $local 'aws-network-outputs.json')
$result
