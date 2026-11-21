#Requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$local = Join-Path $PSScriptRoot '../../.local'
$awsScript = Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'
function Invoke-StateAws([string[]] $Arguments) {
    $value = & $awsScript -AwsArguments ($Arguments + @('--output','json'))
    if ($value) { $value | ConvertFrom-Json -Depth 60 }
}
$identity = Invoke-StateAws -Arguments @('sts','get-caller-identity')
if ($identity.Account -ne '119065066402') { throw 'Cuenta incorrecta.' }
$names = @('ms-auth','ms-events','ms-orders','ms-tickets')
$observed = Invoke-StateAws -Arguments (@('ecs','describe-services','--cluster','eventpass','--services') + @($names | ForEach-Object { "eventpass-$_" }))
if ($observed.failures.Count -or $observed.services.Count -ne 4) { throw 'Faltan servicios. El primer despliegue debe incluir los cuatro.' }
$expected = @{}
foreach ($service in $observed.services) {
    if ($service.networkConfiguration.awsvpcConfiguration.assignPublicIp -ne 'DISABLED') { throw 'Se encontró un servicio fuera de la red privada esperada.' }
    $expected[$service.serviceName] = $service.taskDefinition
}
$expectedFile = Join-Path $local 'sync-expected.json'
$observedFile = Join-Path $local 'sync-observed.json'
$expected | ConvertTo-Json | Set-Content -LiteralPath $expectedFile
$observed | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $observedFile
$inspection = & node (Join-Path $PSScriptRoot 'service-readiness.mjs') $expectedFile $observedFile | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or $inspection.status -ne 'READY') { throw 'ECS aún no está estable. Reintentar al finalizar el despliegue.' }
$network = Invoke-StateAws -Arguments @('cloudformation','describe-stacks','--stack-name','eventpass-network','--query','Stacks[0].Outputs')
$networkFile = Join-Path $local 'aws-network-outputs.json'
$network | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $networkFile
$configFile = Join-Path $local 'aws-task-config.json'
$config = Get-Content -Raw -LiteralPath $configFile | ConvertFrom-Json -AsHashtable
$definitions = @{}
foreach ($name in $names) {
    $service = $observed.services | Where-Object serviceName -eq "eventpass-$name"
    $task = Invoke-StateAws -Arguments @('ecs','describe-task-definition','--task-definition',$service.taskDefinition)
    $container = $task.taskDefinition.containerDefinitions | Where-Object name -eq $name
    if ($container.image -notmatch ('^119065066402\.dkr\.ecr\.us-east-1\.amazonaws\.com/eventpass/' + [regex]::Escape($name) + '@sha256:[a-f0-9]{64}$')) { throw "Imagen no autorizada en $name." }
    $config.images[$name] = $container.image
    $definitions[$name] = $service.taskDefinition
}
foreach ($name in $names) {
    & node (Join-Path $PSScriptRoot 'service-config.mjs') $name $definitions[$name] $networkFile (Join-Path $local "$name-service.json")
    if ($LASTEXITCODE -ne 0) { throw "Definición actual inválida: $name" }
}
$config | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $configFile
@{ SynchronizedAt = [DateTimeOffset]::UtcNow.ToString('o'); definitions = $definitions; images = $config.images } |
    ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $local 'aws-services-deployment.json')
Write-Host 'Estado local sincronizado con las cuatro versiones estables de ECS. No se publicaron imágenes ni se cambiaron servicios.'
