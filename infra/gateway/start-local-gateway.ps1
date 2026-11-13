#Requires -Version 7.0
[CmdletBinding()]
param([switch]$SmokeTest)

$ErrorActionPreference = 'Stop'
$endpoint = 'http://127.0.0.1:4567'
$management = "$endpoint/v2/apis"
$routes = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'routes.json') |
    ConvertFrom-Json -AsHashtable

function New-GatewayResource($Uri, $Payload) {
    Invoke-RestMethod -Method Post -Uri $Uri -ContentType 'application/json' `
        -Body ($Payload | ConvertTo-Json -Depth 6 -Compress) -TimeoutSec 15
}

function Invoke-GatewayCheck {
    param($Method, $Path, [int[]]$ExpectedStatus, $Payload, $AccessToken)

    $headers = @{ Host = $invokeHost }
    if ($AccessToken) {
        $headers.Authorization = "Bearer $AccessToken"
    }
    $request = @{
        Method = $Method
        Uri = "$endpoint$Path"
        Headers = $headers
        TimeoutSec = 15
        SkipHttpErrorCheck = $true
    }
    if ($null -ne $Payload) {
        $request.ContentType = 'application/json'
        $request.Body = $Payload | ConvertTo-Json -Compress
    }

    $response = Invoke-WebRequest @request
    if ($response.StatusCode -notin $ExpectedStatus) {
        throw "$Method $Path devolvio HTTP $($response.StatusCode). Esperado: $($ExpectedStatus -join ', ')."
    }
    Write-Host "$Method $Path -> HTTP $($response.StatusCode)"
    if ($response.Content) {
        $response.Content | ConvertFrom-Json
    }
}

$api = New-GatewayResource $management @{ name = 'eventpass-local'; protocolType = 'HTTP' }
$apiUri = "$management/$($api.apiId)"
$invokeHost = "$($api.apiId).execute-api.localhost.floci.io"

try {
    foreach ($service in $routes.Keys) {
        foreach ($route in $routes[$service]) {
            $method, $path = $route -split ' ', 2
            $integration = New-GatewayResource "$apiUri/integrations" @{
                integrationType = 'HTTP_PROXY'
                integrationMethod = $method
                integrationUri = "http://$service$path"
                payloadFormatVersion = '1.0'
            }
            $null = New-GatewayResource "$apiUri/routes" @{
                routeKey = $route
                target = "integrations/$($integration.integrationId)"
            }
        }
    }
    $null = New-GatewayResource "$apiUri/stages" @{ stageName = '$default'; autoDeploy = $true }

    if ($SmokeTest) {
        $catalog = Invoke-GatewayCheck GET '/api/v1/events?page=0&size=1' 200
        if ($catalog.data.content.Count -gt 1) {
            throw 'Events devolvio mas de un resultado para size=1.'
        }
        if ($catalog.data.content.Count -eq 1) {
            $eventId = $catalog.data.content[0].id
            $event = Invoke-GatewayCheck GET "/api/v1/events/$eventId" 200
            if ($event.data.id -ne $eventId) {
                throw 'El Gateway no conservo el ID de evento.'
            }
        }

        $credentials = @{
            email = "gateway-$([guid]::NewGuid().ToString('N'))@example.com"
            password = [guid]::NewGuid().ToString('N')
        }
        $null = Invoke-GatewayCheck POST '/api/v1/auth/register' 201 $credentials
        $login = Invoke-GatewayCheck POST '/api/v1/auth/login' 200 $credentials
        $token = $login.data.access_token
        if (-not $token) {
            throw 'Auth no devolvio un access_token.'
        }
        $null = Invoke-GatewayCheck GET '/api/v1/orders/my-orders' @(401,403)
        $null = Invoke-GatewayCheck GET '/api/v1/orders/my-orders' 200 -AccessToken $token
        $null = Invoke-GatewayCheck GET '/api/v1/tickets/me' 200 -AccessToken $token
        $null = Invoke-GatewayCheck POST '/api/v1/auth/logout' 200 -AccessToken $token
        $null = Invoke-GatewayCheck GET '/api/v1/orders/my-orders' @(401,403) -AccessToken $token
        $null = Invoke-GatewayCheck GET '/api/v1/tickets/me' @(401,403) -AccessToken $token

        $null = Invoke-GatewayCheck POST '/api/v1/auth/service-token' 404
        $null = Invoke-GatewayCheck PATCH "/api/v1/events/$([guid]::NewGuid())/reserve" 404
        $null = Invoke-GatewayCheck PATCH "/api/v1/events/$([guid]::NewGuid())/release" 404
        $reservationPath = "/api/v1/events/$([guid]::NewGuid())/reservations/$([guid]::NewGuid())"
        $null = Invoke-GatewayCheck PUT $reservationPath 404
        $null = Invoke-GatewayCheck PATCH "$reservationPath/release" 404
        $null = Invoke-GatewayCheck GET '/actuator/health' 404
    }

    [pscustomobject]@{
        ApiId = $api.apiId
        Url = "http://${invokeHost}:4567"
        LoopbackEndpoint = $endpoint
        HostHeader = $invokeHost
    }
}
catch {
    try {
        $null = Invoke-RestMethod -Method Delete -Uri $apiUri -TimeoutSec 15
    }
    catch {
        Write-Warning "No se pudo eliminar la API parcial $($api.apiId)."
    }
    throw
}
