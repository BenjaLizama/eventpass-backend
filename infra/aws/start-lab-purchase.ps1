#Requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$local = Join-Path $PSScriptRoot '../../.local'
$awsScript = Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'
function Invoke-PurchaseAws([string[]] $Arguments, [string] $InputFile) {
    $result = & $awsScript -AwsArguments ($Arguments + @('--output','json')) -InputFile $InputFile
    if ($result) { $result | ConvertFrom-Json -Depth 60 }
}
function Save-PurchaseRequest($Value, [string] $Name) {
    $file = Join-Path $local $Name
    $Value | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $file
    return $file
}
$identity = Invoke-PurchaseAws -Arguments @('sts','get-caller-identity')
if ($identity.Account -ne '119065066402') { throw 'Cuenta incorrecta.' }
$stateFile = Join-Path $local 'aws-dlq-test-state.json'
if ((Test-Path -LiteralPath $stateFile) -and (Get-Content -Raw -LiteralPath $stateFile | ConvertFrom-Json).RestoreNeeded) {
    throw 'Hay una prueba DLQ sin restaurar. Restaurar antes de publicar.'
}
& (Join-Path $PSScriptRoot 'Sync-LabServiceState.ps1')
$queue = Invoke-PurchaseAws -Arguments @('sqs','get-queue-url','--queue-name','eventpass-purchase-pending')
$attributes = (Invoke-PurchaseAws -Arguments @('sqs','get-queue-attributes','--queue-url',$queue.QueueUrl,'--attribute-names','All')).Attributes
$queueFile = Save-PurchaseRequest $attributes 'purchase-queue-attributes.json'
$configFile = Join-Path $local 'purchase-function-config.json'
& node (Join-Path $PSScriptRoot 'purchase-config.mjs') (Join-Path $local 'aws-network-outputs.json') (Join-Path $local 'aws-data-outputs.json') $queueFile $configFile
if ($LASTEXITCODE -ne 0) { throw 'Configuración de Lambda inválida.' }
$config = Get-Content -Raw -LiteralPath $configFile | ConvertFrom-Json -AsHashtable
$name = $config.FunctionName
$image = & (Join-Path $PSScriptRoot 'Publish-LabPurchaseImage.ps1')
$logs = Invoke-PurchaseAws -Arguments @('logs','describe-log-groups','--log-group-name-prefix',"/aws/lambda/$name")
if ("/aws/lambda/$name" -notin $logs.logGroups.logGroupName) {
    Invoke-PurchaseAws -Arguments @('logs','create-log-group','--log-group-name',"/aws/lambda/$name") | Out-Null
}
Invoke-PurchaseAws -Arguments @('logs','put-retention-policy','--log-group-name',"/aws/lambda/$name",'--retention-in-days','7') | Out-Null
$functions = Invoke-PurchaseAws -Arguments @('lambda','list-functions','--query','Functions[].FunctionName')
if ($name -in $functions) {
    $existingFunction = Invoke-PurchaseAws -Arguments @('lambda','get-function-configuration','--function-name',$name)
    if ($existingFunction.PackageType -ne 'Image') { throw 'La función existente no admite imágenes. No se reemplazará ni borrará.' }
    $update = @{} + $config
    $update.Remove('Architectures')
    $update.Remove('PackageType')
    $file = Save-PurchaseRequest $update 'purchase-function-update.json'
    Invoke-PurchaseAws -Arguments @('lambda','update-function-configuration','--cli-input-json','file:///input/request.json') -InputFile $file | Out-Null
    Invoke-PurchaseAws -Arguments @('lambda','wait','function-updated-v2','--function-name',$name) | Out-Null
    Invoke-PurchaseAws -Arguments @('lambda','update-function-code','--function-name',$name,'--image-uri',$image.ImageUri) | Out-Null
} else {
    $create = @{} + $config
    $create.Code = @{ ImageUri = $image.ImageUri }
    $file = Save-PurchaseRequest $create 'purchase-function-create.json'
    Invoke-PurchaseAws -Arguments @('lambda','create-function','--cli-input-json','file:///input/request.json') -InputFile $file | Out-Null
    Invoke-PurchaseAws -Arguments @('lambda','wait','function-active-v2','--function-name',$name) | Out-Null
}
Invoke-PurchaseAws -Arguments @('lambda','wait','function-updated-v2','--function-name',$name) | Out-Null
$current = Invoke-PurchaseAws -Arguments @('lambda','get-function-configuration','--function-name',$name)
if ($current.State -ne 'Active' -or $current.LastUpdateStatus -ne 'Successful' -or $current.PackageType -ne 'Image') { throw 'Lambda no quedó lista como imagen.' }
$payload = Save-PurchaseRequest @{ Records = @() } 'purchase-image-preflight.json'
$preflight = Invoke-PurchaseAws -Arguments @('lambda','invoke','--function-name',$name,'--payload','file:///input/request.json',
    '--cli-binary-format','raw-in-base64-out','/tmp/purchase-response.json') -InputFile $payload
if ($preflight.FunctionError -or $preflight.StatusCode -ne 200) { throw 'La imagen no pudo comunicarse con Auth. Se conserva el consumidor anterior.' }
$existing = (Invoke-PurchaseAws -Arguments @('lambda','list-event-source-mappings','--event-source-arn',$attributes.QueueArn)).EventSourceMappings
$legacyArn = 'arn:aws:lambda:us-east-1:119065066402:function:eventpass-process-purchase'
if ($existing.Count -gt 1 -or ($existing.Count -eq 1 -and $existing[0].FunctionArn -notin @($current.FunctionArn,$legacyArn))) {
    throw 'La cola tiene otro consumidor Lambda. Revisar antes de conectar.'
}
if ($existing.Count -eq 1) {
    $mapping = Invoke-PurchaseAws -Arguments @('lambda','update-event-source-mapping','--uuid',$existing[0].UUID,'--function-name',$name,'--batch-size','1','--maximum-batching-window-in-seconds','0','--scaling-config','MaximumConcurrency=2','--enabled')
} else {
    $mapping = Invoke-PurchaseAws -Arguments @('lambda','create-event-source-mapping','--function-name',$name,'--event-source-arn',$attributes.QueueArn,'--batch-size','1','--maximum-batching-window-in-seconds','0','--scaling-config','MaximumConcurrency=2','--enabled')
}
for ($attempt = 1; $attempt -le 30; $attempt++) {
    $mapping = Invoke-PurchaseAws -Arguments @('lambda','get-event-source-mapping','--uuid',$mapping.UUID)
    if ($mapping.State -eq 'Enabled') { break }
    if ($mapping.State -notin @('Creating','Updating','Enabling')) { throw "Vínculo no disponible: $($mapping.State)" }
    Start-Sleep -Seconds 5
}
if ($mapping.State -ne 'Enabled' -or $mapping.FunctionArn -ne $current.FunctionArn) { throw 'El vínculo SQS no quedó habilitado en la función de imagen.' }
@{ FunctionArn = $current.FunctionArn; MappingId = $mapping.UUID; QueueArn = $attributes.QueueArn;
    QueueUrl = $queue.QueueUrl; State = $mapping.State; ImageUri = $image.ImageUri; Revision = $image.Revision;
    PreviousFunctionArn = $(if ($existing.Count -eq 1) { $existing[0].FunctionArn } else { $null }) } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $local 'aws-purchase-deployment.json')
$deployFile = Join-Path $local 'aws-deploy-config.json'
if (Test-Path -LiteralPath $deployFile) {
    $deployConfig = Get-Content -Raw -LiteralPath $deployFile | ConvertFrom-Json -AsHashtable
    $deployConfig.purchaseFunction = $name
    $deployConfig.purchaseRepository = 'eventpass/purchase-lambda'
    $deployConfig | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath $deployFile
}
Write-Host 'Lambda ejecuta la imagen Docker de ECR en subredes privadas; un único vínculo SQS habilitado. La función ZIP anterior queda sin este consumidor.'
