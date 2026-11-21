#Requires -Version 7.0
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$awsScript = Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'
$local = Join-Path $PSScriptRoot '../../.local'
$network = Get-Content -Raw -LiteralPath (Join-Path $local 'aws-network-outputs.json') | ConvertFrom-Json
$gateway = ($network | Where-Object OutputKey -eq 'GatewayUrl').OutputValue.TrimEnd('/')
$settings = Get-Content -Raw -LiteralPath (Join-Path $local 'aws-task-config.json') | ConvertFrom-Json
if ($gateway -notmatch '^https://[a-z0-9]+\.execute-api\.us-east-1\.amazonaws\.com$') { throw 'Gateway inesperado.' }
$checks = [Collections.Generic.List[object]]::new()
function Invoke-Probe([string] $Method, [string] $Path, [int] $Expected, $Body = $null, [string] $Token = '') {
    $parameters = @{ Uri = $gateway + $Path; Method = $Method; SkipHttpErrorCheck = $true;
        TimeoutSec = 30; MaximumRedirection = 0; Headers = @{} }
    if ($Token) { $parameters.Headers.Authorization = 'Bearer ' + $Token }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 12 -Compress; $parameters.ContentType = 'application/json' }
    $response = Invoke-WebRequest @parameters
    $checks.Add(@{ Method = $Method; Path = $Path; Status = [int]$response.StatusCode; Expected = $Expected })
    if ([int]$response.StatusCode -ne $Expected) {
        # Do not print request bodies, JWTs, passwords or full responses.
        throw "$Method $Path respondió HTTP $($response.StatusCode); se esperaba $Expected."
    }
    Write-Host "$Method $Path -> HTTP $($response.StatusCode)"
    if ($response.Content) {
        try { return ($response.Content | ConvertFrom-Json -Depth 30).data }
        catch { throw "Respuesta JSON inválida en $Method $Path." }
    }
}
function Assert-Probe($Condition, [string] $Message) { if (-not $Condition) { throw $Message } }

$adminToken = ''
$customerToken = ''
$eventId = ''
$orderId = ''
$orderCancelled = $false
$adminPassword = $null
try {
    $identity = & $awsScript -AwsArguments @('sts','get-caller-identity','--output','json') | ConvertFrom-Json
    Assert-Probe ($identity.Account -eq '119065066402') 'Cuenta AWS incorrecta.'
    Invoke-Probe 'GET' '/api/v1/events?page=0&size=1' 200 | Out-Null
    Invoke-Probe 'GET' '/api/v1/orders/my-orders' 401 | Out-Null
    Invoke-Probe 'GET' '/api/v1/tickets/me' 401 | Out-Null
    Invoke-Probe 'POST' '/api/v1/auth/service-token' 404 @{} | Out-Null
    Invoke-Probe 'GET' '/actuator/health' 404 | Out-Null
    $secretArn = $settings.secrets.ADMIN_PASSWORD -replace ':password::$',''
    $secret = & $awsScript -AwsArguments @('secretsmanager','get-secret-value','--secret-id',$secretArn,'--output','json') | ConvertFrom-Json
    try { $adminPassword = ($secret.SecretString | ConvertFrom-Json).password }
    catch { throw 'No se pudo interpretar el secreto de administrador.' }
    $secret = $null
    $admin = Invoke-Probe 'POST' '/api/v1/auth/login' 200 @{ email = $settings.environment.ADMIN_EMAIL; password = $adminPassword }
    $adminPassword = $null
    $adminToken = $admin.access_token
    Assert-Probe ([bool]$adminToken) 'El login no devolvió el token de administrador.'
    $runId = [guid]::NewGuid().ToString('N')
    $password = [guid]::NewGuid().ToString('N')
    $email = "deploy-$runId@example.com"
    $registered = Invoke-Probe 'POST' '/api/v1/auth/register' 201 @{ email = $email; password = $password }
    $customerToken = $registered.access_token
    Invoke-Probe 'GET' '/api/v1/auth/me' 200 $null $customerToken | Out-Null
    Invoke-Probe 'POST' '/api/v1/auth/logout' 200 $null $customerToken | Out-Null
    $customerToken = ''
    $login = Invoke-Probe 'POST' '/api/v1/auth/login' 200 @{ email = $email; password = $password }
    $password = $null
    $customerToken = $login.access_token
    $refreshToken = $login.refresh_token
    $refreshed = Invoke-Probe 'POST' '/api/v1/auth/refresh' 200 @{ refreshToken = $refreshToken }
    Assert-Probe ([bool]$refreshed.access_token) 'No se pudo renovar el token.'
    $customerToken = $refreshed.access_token
    Invoke-Probe 'GET' '/api/v1/orders/my-orders' 200 $null $customerToken | Out-Null
    Invoke-Probe 'GET' '/api/v1/tickets/me' 200 $null $customerToken | Out-Null
    Invoke-Probe 'POST' '/api/v1/venues' 403 @{ name = 'Denied'; address = 'Calle de prueba 123'; city = 'Santiago'; country = 'Chile'; capacity = 3 } $customerToken | Out-Null
    $venue = Invoke-Probe 'POST' '/api/v1/venues' 201 @{ name = "Prueba AWS $runId"; address = 'Recinto de prueba'; city = 'Santiago'; country = 'Chile'; capacity = 3 } $adminToken
    $start = [DateTimeOffset]::UtcNow.AddDays(7)
    $event = Invoke-Probe 'POST' '/api/v1/events' 201 @{
        title = "Verificación AWS $runId"; description = 'Prueba de despliegue de microservicios'; category = 'CONCERT'
        startDate = $start.ToString('o'); endDate = $start.AddHours(2).ToString('o'); venueId = $venue.id
        ticketCategories = @(@{ name = 'General'; price = 10000; totalCapacity = 3; maxPerUser = 2 })
    } $adminToken
    $eventId = $event.id
    $categoryId = $event.ticketCategories[0].id
    $published = Invoke-Probe 'PATCH' "/api/v1/events/$eventId/publish" 200 $null $adminToken
    Assert-Probe ($published.status -eq 'PUBLISHED') 'El evento no quedó publicado.'
    Invoke-Probe 'PATCH' "/api/v1/events/$eventId/reserve" 404 @{} $adminToken | Out-Null
    $order = Invoke-Probe 'POST' '/api/v1/orders' 201 @{ eventId = $eventId; ticketCategoryId = $categoryId; quantity = 1 } $customerToken
    $orderId = $order.id
    Assert-Probe ($order.status -eq 'AWAITING_PAYMENT') 'La orden debe esperar el pago simulado.'
    Assert-Probe ([decimal]$order.totalAmount -eq 10000) 'El total de la orden es incorrecto.'
    $cancelled = Invoke-Probe 'PATCH' "/api/v1/orders/$orderId/cancel" 200 $null $customerToken
    $orderCancelled = $cancelled.status -eq 'CANCELLED'
    Assert-Probe $orderCancelled 'La orden no se canceló.'
    $category = Invoke-Probe 'GET' "/api/v1/events/$eventId/ticket-categories/$categoryId" 200
    Assert-Probe ($category.availableCapacity -eq 3) 'La orden sin pago alteró el aforo.'
    Invoke-Probe 'POST' '/api/v1/auth/logout' 200 $null $customerToken | Out-Null
    Invoke-Probe 'GET' '/api/v1/orders/my-orders' 401 $null $customerToken | Out-Null
    Invoke-Probe 'GET' '/api/v1/tickets/me' 401 $null $customerToken | Out-Null
    Invoke-Probe 'POST' '/api/v1/auth/refresh' 401 @{ refreshToken = $refreshToken } | Out-Null
    $customerToken = ''
    @{ gateway = $gateway; checks = $checks; eventId = $eventId; orderId = $orderId;
        paymentExecuted = $false } | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $local 'aws-services-smoke.json')
    Write-Host 'OK: Gateway, sesión, roles, DynamoDB, comunicación Auth/Events/Orders, creación y cancelación de orden.'
    Write-Host 'Este script no ejecuta pagos. Usa test-lab-purchase.ps1 para comprobar pago y emisión asíncrona.'
} finally {
    $adminPassword = $null
    if ($orderId -and -not $orderCancelled -and $customerToken) {
        try { Invoke-Probe 'PATCH' "/api/v1/orders/$orderId/cancel" 200 $null $customerToken | Out-Null } catch { Write-Warning 'Revisar la orden de prueba que no pudo cancelarse.' }
    }
    if ($eventId -and $adminToken) {
        try { Invoke-Probe 'PATCH' "/api/v1/events/$eventId/cancel" 200 $null $adminToken | Out-Null } catch { Write-Warning 'Revisar el evento de prueba que no pudo cancelarse.' }
    }
    foreach ($token in @($customerToken, $adminToken)) {
        if ($token) { try { Invoke-Probe 'POST' '/api/v1/auth/logout' 200 $null $token | Out-Null } catch {} }
    }
}
