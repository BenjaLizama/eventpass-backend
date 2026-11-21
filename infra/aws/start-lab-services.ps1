#Requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateNotNullOrEmpty()]
    [ValidateSet('ms-auth','ms-events','ms-orders','ms-tickets')]
    [string[]] $Services = @('ms-auth','ms-events','ms-orders','ms-tickets')
)

$ErrorActionPreference = 'Stop'
$awsScript = Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'
function Invoke-ServiceAws([string[]] $Arguments, [string] $InputFile) {
    $output = & $awsScript -AwsArguments ($Arguments + @('--output', 'json')) -InputFile $InputFile
    if ($output) { $output | ConvertFrom-Json -Depth 60 }
}
$identity = Invoke-ServiceAws -Arguments @('sts', 'get-caller-identity')
if ($identity.Account -ne '119065066402') { throw 'Cuenta incorrecta. No se publicará nada.' }
$local = Join-Path $PSScriptRoot '../../.local'
$dirty = & git -c safe.directory='*' status --porcelain -- $services
if ($LASTEXITCODE -ne 0 -or $dirty) { throw 'Guarda los cambios de los microservicios en un commit antes de publicar imágenes.' }
$revision = & git -c safe.directory='*' rev-parse HEAD
if ($revision -notmatch '^[a-f0-9]{40}$') { throw 'No se pudo identificar el commit.' }
foreach ($name in @('eventpass-network','eventpass-data')) {
    $stack = (Invoke-ServiceAws -Arguments @('cloudformation','describe-stacks','--stack-name',$name)).Stacks[0]
    if ($stack.StackStatus -notin @('CREATE_COMPLETE','UPDATE_COMPLETE')) { throw "$name no está disponible." }
    $file = if ($name -eq 'eventpass-network') { 'aws-network-outputs.json' } else { 'aws-data-outputs.json' }
    $stack.Outputs | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $local $file)
}
$proof = Get-Content -Raw -LiteralPath (Join-Path $local 'aws-data-verification.json') | ConvertFrom-Json
if ($proof.exitCode -ne 0) { throw 'Falta la verificación de la capa de datos.' }
& node (Join-Path $PSScriptRoot 'task-settings.mjs') (Join-Path $local 'aws-network-outputs.json') (Join-Path $local 'aws-data-outputs.json') (Join-Path $local 'aws-task-settings.json')
if ($LASTEXITCODE -ne 0) { throw 'Configuración de tareas inválida.' }
$config = Get-Content -Raw -LiteralPath (Join-Path $local 'aws-task-settings.json') | ConvertFrom-Json -AsHashtable
$configFile = Join-Path $local 'aws-task-config.json'
if (Test-Path -LiteralPath $configFile) {
    $previous = Get-Content -Raw -LiteralPath $configFile | ConvertFrom-Json -AsHashtable
    foreach ($name in $previous.images.Keys) { $config.images[$name] = $previous.images[$name] }
}
foreach ($name in @('ms-auth','ms-events','ms-orders','ms-tickets')) {
    if ($name -notin $services -and -not $config.images[$name]) {
        throw 'El primer despliegue debe publicar los cuatro microservicios.'
    }
}
$registry = '119065066402.dkr.ecr.us-east-1.amazonaws.com'
$password = & $awsScript -AwsArguments @('ecr','get-login-password')
try {
    $password | & docker login --username AWS --password-stdin $registry
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo autenticar Docker en ECR.' }
} finally { $password = $null }
$tag = $revision + '-' + [guid]::NewGuid().ToString('N')
foreach ($service in $services) {
    Write-Host "Construyendo y publicando $service"
    $image = "${registry}/eventpass/${service}:$tag"
    & docker build --platform linux/amd64 --label "org.opencontainers.image.revision=$revision" -t $image (Join-Path $PSScriptRoot "../../$service")
    if ($LASTEXITCODE -ne 0) { throw "No se pudo construir $service." }
    & docker push $image
    if ($LASTEXITCODE -ne 0) { throw "No se pudo publicar $service." }
    $info = Invoke-ServiceAws -Arguments @('ecr','describe-images','--repository-name',"eventpass/$service",'--image-ids',"imageTag=$tag")
    $digest = $info.imageDetails[0].imageDigest
    if ($digest -notmatch '^sha256:[a-f0-9]{64}$') { throw "Digest inválido de $service." }
    $config.images[$service] = "${registry}/eventpass/$service@$digest"
}
$config | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath $configFile
$tasksDirectory = Join-Path $local 'ecs-tasks'
& node (Join-Path $PSScriptRoot 'render-task-definitions.mjs') $configFile $tasksDirectory
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron generar las cuatro tareas.' }
$definitions = @{}
foreach ($service in $services) {
    $registered = Invoke-ServiceAws -Arguments @('ecs','register-task-definition','--cli-input-json','file:///input/request.json') -InputFile (Join-Path $tasksDirectory "$service.json")
    $definitions[$service] = $registered.taskDefinition.taskDefinitionArn
}
$current = Invoke-ServiceAws -Arguments (@('ecs','describe-services','--cluster','eventpass','--services') + @($services | ForEach-Object { "eventpass-$_" }))
foreach ($service in $services) {
    $file = Join-Path $local "$service-service.json"
    & node (Join-Path $PSScriptRoot 'service-config.mjs') $service $definitions[$service] (Join-Path $local 'aws-network-outputs.json') $file
    if ($LASTEXITCODE -ne 0) { throw "Servicio inválido: $service" }
    $existing = $current.services | Where-Object { $_.serviceName -eq "eventpass-$service" -and $_.status -eq 'ACTIVE' }
    if ($existing) {
        if ($existing.networkConfiguration.awsvpcConfiguration.assignPublicIp -ne 'DISABLED') { throw 'El servicio existente no es privado.' }
        Invoke-ServiceAws -Arguments @('ecs','update-service','--cluster','eventpass','--service',"eventpass-$service",'--task-definition',$definitions[$service]) | Out-Null
    } else {
        Invoke-ServiceAws -Arguments @('ecs','create-service','--cli-input-json','file:///input/request.json') -InputFile $file | Out-Null
    }
    Write-Host "Servicio configurado: $service"
}
& (Join-Path $PSScriptRoot 'Wait-LabServices.ps1')
foreach ($service in @('ms-auth','ms-events','ms-orders','ms-tickets')) {
    $request = Get-Content -Raw -LiteralPath (Join-Path $local "$service-service.json") | ConvertFrom-Json
    $definitions[$service] = $request.taskDefinition
}
$deployment = @{ revision = $revision; definitions = $definitions; images = $config.images }
$deployment | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $local 'aws-services-deployment.json')
Write-Host 'Los cuatro microservicios quedaron estables en ECS.'
