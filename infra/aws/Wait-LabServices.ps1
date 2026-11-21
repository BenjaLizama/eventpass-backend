#Requires -Version 7.0
[CmdletBinding()]
param([int] $Attempts = 60)
$ErrorActionPreference = 'Stop'
$local = Join-Path $PSScriptRoot '../../.local'
$expected = @{}
foreach ($service in @('ms-auth','ms-events','ms-orders','ms-tickets')) {
    $config = Get-Content -Raw -LiteralPath (Join-Path $local "$service-service.json") | ConvertFrom-Json
    $expected[$config.serviceName] = $config.taskDefinition
}
$expectedFile = Join-Path $local 'service-expected-definitions.json'
$observedFile = Join-Path $local 'service-observed.json'
$expected | ConvertTo-Json | Set-Content -LiteralPath $expectedFile
for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
    $arguments = @('ecs','describe-services','--cluster','eventpass','--services') + @($expected.Keys) + @('--output','json')
    $observed = & (Join-Path $PSScriptRoot 'Invoke-LabAws.ps1') -AwsArguments $arguments
    $observed | Set-Content -LiteralPath $observedFile
    $result = & node (Join-Path $PSScriptRoot 'service-readiness.mjs') $expectedFile $observedFile | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo comprobar el estado del despliegue.' }
    if ($result.status -eq 'FAILED') { throw $result.reason }
    if ($result.status -eq 'READY') { Write-Host 'Los cuatro servicios ejecutan las versiones solicitadas y completaron el despliegue.'; return }
    Write-Host "Esperando despliegue ($attempt/$Attempts): $($result.pending -join ', ')"
    if ($attempt -lt $Attempts) { Start-Sleep -Seconds 10 }
}
throw 'La versión solicitada no quedó activa a tiempo. Revisar eventos y rollback en ECS.'
