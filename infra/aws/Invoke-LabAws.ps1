#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0)]
    [string[]] $AwsArguments,
    [string] $InputFile
)

$ErrorActionPreference = 'Stop'
$sessionPath = Join-Path $PSScriptRoot '../../.local/aws-lab-session.xml'
if (-not (Test-Path -LiteralPath $sessionPath)) {
    throw 'Primero ejecuta ./infra/aws/connect-learner-lab.ps1 en PowerShell 7.'
}
$session = Import-Clixml -LiteralPath $sessionPath
if ($session.AccountId -ne '119065066402' -or $session.Region -ne 'us-east-1') {
    throw 'La sesión guardada no corresponde a la cuenta y región de EventPass.'
}
$names = @('AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_SESSION_TOKEN')
$previous = @{}
foreach ($name in $names) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    foreach ($name in $names) {
        [Environment]::SetEnvironmentVariable($name, ($session.$name | ConvertFrom-SecureString -AsPlainText), 'Process')
    }
    $dockerArguments = @('run', '--rm', '--env', 'AWS_ACCESS_KEY_ID', '--env', 'AWS_SECRET_ACCESS_KEY',
        '--env', 'AWS_SESSION_TOKEN', '--env', 'AWS_EC2_METADATA_DISABLED=true')
    if ($InputFile) {
        $resolvedInput = (Resolve-Path -LiteralPath $InputFile).Path
        if (-not (Test-Path -LiteralPath $resolvedInput -PathType Leaf)) { throw 'InputFile debe ser un archivo.' }
        $dockerArguments += @('--mount', "type=bind,source=$resolvedInput,target=/input/request.json,readonly")
    }
    $dockerArguments += @('public.ecr.aws/aws-cli/aws-cli:2.41.1')
    $dockerArguments += $AwsArguments
    $dockerArguments += @('--region', $session.Region, '--no-cli-pager')
    & docker @dockerArguments
    if ($LASTEXITCODE -ne 0) { throw 'La llamada AWS falló. Revisa permisos o renueva la sesión del laboratorio.' }
} finally {
    foreach ($name in $names) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}
