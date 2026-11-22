#Requires -Version 7.0
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'Este asistente protege las claves con la cuenta de Windows y requiere Windows.' }
Get-Command docker -ErrorAction Stop | Out-Null
try {
    & docker info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker no disponible.' }
} catch {
    throw 'Docker Desktop no está listo. Ábrelo en modo Linux, espera a que el motor esté en ejecución y repite este comando. Aún no se solicitaron ni guardaron claves.'
}
Write-Host 'Abre AWS Academy > AWS Details > AWS CLI. Copia solo el valor de cada campo.'
Write-Host 'Los tres valores se introducen ocultos y se guardan cifrados para tu usuario de Windows.'
$session = [pscustomobject]@{
    AccountId = '119065066402'
    Region = 'us-east-1'
    AWS_ACCESS_KEY_ID = Read-Host 'aws_access_key_id' -AsSecureString
    AWS_SECRET_ACCESS_KEY = Read-Host 'aws_secret_access_key' -AsSecureString
    AWS_SESSION_TOKEN = Read-Host 'aws_session_token' -AsSecureString
}
foreach ($name in @('AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_SESSION_TOKEN')) {
    if ($session.$name.Length -eq 0) { throw "Falta $name. No se guardó la sesión." }
}
$localDirectory = Join-Path $PSScriptRoot '../../.local'
New-Item -ItemType Directory -Path $localDirectory -Force | Out-Null
$sessionPath = Join-Path $localDirectory 'aws-lab-session.xml'
$previousSession = if (Test-Path -LiteralPath $sessionPath) { [IO.File]::ReadAllText($sessionPath) } else { $null }
try {
    $session | Export-Clixml -LiteralPath $sessionPath
    $identity = & (Join-Path $PSScriptRoot 'Invoke-LabAws.ps1') -AwsArguments @('sts', 'get-caller-identity', '--output', 'json') | ConvertFrom-Json
    if ($identity.Account -ne $session.AccountId) { throw 'Las claves pertenecen a otra cuenta AWS.' }
    Write-Host "Conexión verificada: cuenta $($identity.Account), región $($session.Region)."
    Write-Host 'Avísame que la conexión quedó verificada. No envíes las claves por el chat.'
} catch {
    if ($null -ne $previousSession) { [IO.File]::WriteAllText($sessionPath, $previousSession) }
    else { Remove-Item -LiteralPath $sessionPath -ErrorAction SilentlyContinue }
    throw
}
