#Requires -Version 7.0
[CmdletBinding()]
param([switch]$Build, [switch]$SmokeTest)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root = Join-Path $PSScriptRoot '../..'

function Wait-LocalHttp([string]$Uri) {
    for ($attempt = 1; $attempt -le 15; $attempt++) {
        try {
            $null = Invoke-WebRequest $Uri -TimeoutSec 10
            return
        }
        catch {
            if ($attempt -eq 15) { throw }
            Start-Sleep -Seconds 2
        }
    }
}

Push-Location $root
try {
    docker info --format '{{.ServerVersion}}' | Out-Null
    $buildArgs = if ($Build) { @('--build') } else { @() }
    docker compose -f docker-compose.yml -f infra/gateway/compose.private.yaml up -d --wait --wait-timeout 240 @buildArgs
    $compose = @('compose', '-p', 'eventpass-gateway', '-f', 'infra/gateway/compose.yaml')
    docker @compose up -d
    Wait-LocalHttp 'http://127.0.0.1:4567/_floci/health'
    $gateway = & (Join-Path $PSScriptRoot 'start-local-gateway.ps1') -SmokeTest:$SmokeTest

    $previousApiId = $env:EVENTPASS_API_ID
    try {
        $env:EVENTPASS_API_ID = $gateway.ApiId
        docker @compose -f infra/gateway/compose.swagger.yaml up -d
        $swagger = 'http://127.0.0.1:8090'
        Wait-LocalHttp "$swagger/openapi.json"
        Wait-LocalHttp "$swagger/api/v1/events?page=0&size=1"
        [pscustomobject]@{ Swagger = "$swagger/"; Gateway = $gateway.Url }
    }
    finally {
        $env:EVENTPASS_API_ID = $previousApiId
    }
}
finally {
    Pop-Location
}
