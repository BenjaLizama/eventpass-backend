#Requires -Version 7.0
[CmdletBinding()]
param([switch] $Apply)
$ErrorActionPreference = 'Stop'
$awsScript = Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'
$local = Join-Path $PSScriptRoot '../../.local'
$stackName = 'eventpass-monitoring'
function Invoke-MonitoringAws([string[]] $Arguments, [string] $InputFile) {
    $value = & $awsScript -AwsArguments ($Arguments + @('--output','json')) -InputFile $InputFile
    if ($value) { $value | ConvertFrom-Json -Depth 60 }
}
$identity = Invoke-MonitoringAws -Arguments @('sts','get-caller-identity')
if ($identity.Account -ne '119065066402') { throw 'Cuenta incorrecta.' }
$network = (Invoke-MonitoringAws -Arguments @('cloudformation','describe-stacks','--stack-name','eventpass-network')).Stacks[0]
if ($network.StackStatus -notin @('CREATE_COMPLETE','UPDATE_COMPLETE')) { throw 'La red no está estable.' }
$network.Outputs | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $local 'aws-network-outputs.json')
$template = Join-Path $local 'monitoring-template.json'
& node (Join-Path $PSScriptRoot 'monitoring-template.mjs') (Join-Path $local 'aws-network-outputs.json') (Join-Path $local 'aws-purchase-deployment.json') $template
if ($LASTEXITCODE -ne 0) { throw 'Plantilla de monitoreo inválida.' }
Invoke-MonitoringAws -Arguments @('cloudformation','validate-template','--template-body','file:///input/request.json') -InputFile $template | Out-Null
$stacks = Invoke-MonitoringAws -Arguments @('cloudformation','list-stacks','--query','StackSummaries[].{Name:StackName,Status:StackStatus}')
$stack = $stacks | Where-Object { $_.Name -eq $stackName -and $_.Status -ne 'DELETE_COMPLETE' } | Select-Object -First 1
if ($stack -and $stack.Status -notin @('CREATE_COMPLETE','UPDATE_COMPLETE','REVIEW_IN_PROGRESS')) { throw "Monitoreo en $($stack.Status). Revisar eventos." }
$type = if (-not $stack -or $stack.Status -eq 'REVIEW_IN_PROGRESS') { 'CREATE' } else { 'UPDATE' }
$change = Invoke-MonitoringAws -Arguments @('cloudformation','create-change-set','--stack-name',$stackName,
    '--change-set-name',('eventpass-' + [guid]::NewGuid().ToString('N')),'--change-set-type',$type,
    '--template-body','file:///input/request.json','--tags','Key=Project,Value=eventpass') -InputFile $template
$hasChanges = $true
try { Invoke-MonitoringAws -Arguments @('cloudformation','wait','change-set-create-complete','--change-set-name',$change.Id) | Out-Null }
catch {
    $detail = Invoke-MonitoringAws -Arguments @('cloudformation','describe-change-set','--change-set-name',$change.Id)
    if ($detail.StatusReason -like "*didn't contain changes*" -or $detail.StatusReason -like '*No updates are to be performed*') { $hasChanges = $false }
    else { throw "No se pudo preparar monitoreo: $($detail.StatusReason)" }
}
if ($hasChanges) {
    $detail = Invoke-MonitoringAws -Arguments @('cloudformation','describe-change-set','--change-set-name',$change.Id)
    $changes = @($detail.Changes.ResourceChange)
    $changes | Select-Object Action,LogicalResourceId,ResourceType,Replacement | Format-Table | Out-Host
    if ($changes | Where-Object { $_.Action -eq 'Remove' -or ($_.Replacement -and $_.Replacement -ne 'False') }) { throw 'El plan reemplaza o elimina recursos. No se aplicó.' }
    if (-not $Apply) { Write-Host "Plan listo: $($change.Id)"; return }
    Invoke-MonitoringAws -Arguments @('cloudformation','execute-change-set','--change-set-name',$change.Id) | Out-Null
    $waiter = if ($type -eq 'CREATE') { 'stack-create-complete' } else { 'stack-update-complete' }
    Invoke-MonitoringAws -Arguments @('cloudformation','wait',$waiter,'--stack-name',$stackName) | Out-Null
}
$outputs = Invoke-MonitoringAws -Arguments @('cloudformation','describe-stacks','--stack-name',$stackName,'--query','Stacks[0].Outputs')
$outputs | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $local 'aws-monitoring-outputs.json')
Write-Host 'Panel eventpass-lab y nueve alarmas disponibles en CloudWatch, sin acciones externas.'
