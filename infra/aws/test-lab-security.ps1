#Requires -Version 7.0
[CmdletBinding()]
param([string] $AwsScript = (Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'))
$ErrorActionPreference = 'Stop'
$local = Join-Path $PSScriptRoot '../../.local'
function Invoke-SecurityAws([string[]] $Arguments) {
    $value = & $AwsScript -AwsArguments ($Arguments + @('--output','json'))
    if ($value) { $value | ConvertFrom-Json -Depth 40 }
}
if ((Invoke-SecurityAws -Arguments @('sts','get-caller-identity')).Account -ne '119065066402') { throw 'Cuenta incorrecta.' }
$network = (Invoke-SecurityAws -Arguments @('cloudformation','describe-stacks','--stack-name','eventpass-network')).Stacks[0].Outputs
$api = ($network | Where-Object OutputKey -eq Api).OutputValue
$gateway = ($network | Where-Object OutputKey -eq GatewayUrl).OutputValue
$authorizers = Invoke-SecurityAws -Arguments @('apigatewayv2','get-authorizers','--api-id',$api)
$authorizer = @($authorizers.Items | Where-Object Name -eq 'eventpass-session-and-role')
if ($authorizer.Count -ne 1 -or $authorizer[0].AuthorizerType -ne 'REQUEST' -or $authorizer[0].AuthorizerResultTtlInSeconds -ne 0) { throw 'Autorizador inexistente, duplicado o con caché.' }
$function = Invoke-SecurityAws -Arguments @('lambda','get-function-configuration','--function-name','eventpass-gateway-authorizer')
$expectedGroup = ($network | Where-Object OutputKey -eq AuthorizerSecurityGroup).OutputValue
if ($function.State -ne 'Active' -or $function.PackageType -ne 'Image' -or
    $function.VpcConfig.SecurityGroupIds.Count -ne 1 -or $function.VpcConfig.SecurityGroupIds[0] -ne $expectedGroup -or
    $function.Environment.Variables.PSObject.Properties.Name -contains 'JWT_SECRET') { throw 'Lambda fuera de la configuración privada esperada.' }
$permission = Invoke-SecurityAws -Arguments @('lambda','get-policy','--function-name','eventpass-gateway-authorizer')
$statements = ($permission.Policy | ConvertFrom-Json).Statement
$source = "arn:aws:execute-api:us-east-1:119065066402:$api/authorizers/$($authorizer[0].AuthorizerId)"
$scoped = @($statements | Where-Object { $_.Effect -eq 'Allow' -and $_.Principal.Service -eq 'apigateway.amazonaws.com' -and
    $_.Condition.ArnLike.'AWS:SourceArn' -eq $source -and $_.Condition.StringEquals.'AWS:SourceAccount' -eq '119065066402' })
if ($scoped.Count -ne 1 -or @($statements | Where-Object { $_.Effect -eq 'Allow' }).Count -ne 1) { throw 'Permiso de invocación del autorizador no restringido a esta API/cuenta.' }
$routes = Invoke-SecurityAws -Arguments @('apigatewayv2','get-routes','--api-id',$api)
$policy = Get-Content -Raw (Join-Path $PSScriptRoot '../gateway-authorizer/src/main/resources/route-policy.json') | ConvertFrom-Json -AsHashtable
if ($routes.Items.Count -ne $policy.Count) { throw 'El Gateway no coincide con la lista de rutas.' }
foreach ($route in $routes.Items) {
    if (-not $policy.ContainsKey($route.RouteKey)) { throw 'Ruta externa no autorizada.' }
    if ($policy[$route.RouteKey] -contains 'PUBLIC') {
        if ($route.AuthorizationType -ne 'NONE') { throw 'Ruta pública mal configurada.' }
    } elseif ($route.AuthorizationType -ne 'CUSTOM' -or $route.AuthorizerId -ne $authorizer[0].AuthorizerId) { throw 'Ruta protegida sin autorizador.' }
}
$settings = Get-Content -Raw (Join-Path $local 'aws-task-config.json') | ConvertFrom-Json
$secretArn = $settings.secrets.ADMIN_PASSWORD -replace ':password::$',''
$previous = @{}
$names = @('AUTH_BASE_URL','EVENTS_BASE_URL','ORDERS_BASE_URL','TICKETS_BASE_URL','E2E_ADMIN_EMAIL','E2E_ADMIN_PASSWORD')
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $started = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $secret = Invoke-SecurityAws -Arguments @('secretsmanager','get-secret-value','--secret-id',$secretArn)
    $password = ($secret.SecretString | ConvertFrom-Json).password; $secret = $null
    if ($env:GITHUB_ACTIONS -eq 'true') { Write-Output "::add-mask::$password" }
    foreach ($name in @('AUTH_BASE_URL','EVENTS_BASE_URL','ORDERS_BASE_URL','TICKETS_BASE_URL')) { [Environment]::SetEnvironmentVariable($name,$gateway,'Process') }
    [Environment]::SetEnvironmentVariable('E2E_ADMIN_EMAIL',$settings.environment.ADMIN_EMAIL,'Process')
    [Environment]::SetEnvironmentVariable('E2E_ADMIN_PASSWORD',$password,'Process'); $password = $null
    $wrapper = if ($IsWindows) { 'mvnw.cmd' } else { 'mvnw' }
    & (Join-Path $PSScriptRoot "../../ms-auth/$wrapper") -B -f (Join-Path $PSScriptRoot '../../e2e-tests/pom.xml') '-Dit.test=SecurityBoundariesIT,OrganizerOwnershipIT' verify
    if ($LASTEXITCODE -ne 0) { throw 'Falló la comprobación de privilegios/JWT en AWS.' }
    $observed = $false
    for ($attempt = 0; $attempt -lt 15; $attempt++) {
        $log = Invoke-SecurityAws -Arguments @('logs','filter-log-events','--log-group-name','/aws/lambda/eventpass-gateway-authorizer',
            '--start-time',[string]$started,'--filter-pattern','"JWT authorization request"','--limit','1')
        if ($log.events.Count -gt 0) { $observed = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $observed) { throw 'No se confirmó la ejecución del autorizador durante las peticiones al Gateway.' }
    @{CheckedAt=[DateTimeOffset]::UtcNow.ToString('o');Api=$api;Authorizer=$authorizer[0].AuthorizerId;CacheSeconds=0;Routes=$routes.Items.Count;Result='PASS'} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $local 'aws-security-smoke.json')
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
    $previous = $null; $password = $null; $secret = $null
}
