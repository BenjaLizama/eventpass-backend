#Requires -Version 7.0
[CmdletBinding()]
param([switch] $EnableDeployment, [string] $GitHubCli)
$ErrorActionPreference = 'Stop'
$local = Join-Path $PSScriptRoot '../../.local'
$repository = 'BenjaLizama/eventpass-backend'
$identity = & (Join-Path $PSScriptRoot 'Invoke-LabAws.ps1') -AwsArguments @('sts','get-caller-identity','--output','json') | ConvertFrom-Json
if ($identity.Account -ne '119065066402') { throw 'Cuenta AWS incorrecta.' }
& (Join-Path $PSScriptRoot 'Invoke-LabAws.ps1') -AwsArguments @('ecs','describe-clusters','--clusters','eventpass','--query','clusters[].clusterName','--output','json') | Out-Null
$revision = & git -c safe.directory='*' rev-parse HEAD
& node --input-type=module -e 'import {readFileSync} from "node:fs"; import {validateConfig} from "./infra/aws/deploy-lab.mjs"; validateConfig(JSON.parse(readFileSync(".local/aws-deploy-config.json")), JSON.parse(readFileSync(".local/aws-task-config.json")), process.argv[1]);' $revision
if ($LASTEXITCODE -ne 0) { throw 'Configuración del pipeline inválida.' }
if (-not $GitHubCli) {
    $command = Get-Command gh -ErrorAction SilentlyContinue
    $GitHubCli = if ($command) { $command.Source } else { Join-Path $local 'tools/gh.exe' }
}
$GitHubCli = (Resolve-Path -LiteralPath $GitHubCli).Path
$session = Import-Clixml -LiteralPath (Join-Path $local 'aws-lab-session.xml')
if ($session.AccountId -ne '119065066402' -or $session.Region -ne 'us-east-1') { throw 'Sesión guardada incorrecta.' }
$raw = ("protocol=https`nhost=github.com`n`n" | git credential fill) -join "`n"
$match = [regex]::Match($raw, '(?m)^password=(.+)$')
if (-not $match.Success) { throw 'No hay una sesión GitHub disponible en Git Credential Manager.' }
$token = $match.Groups[1].Value.Trim(); $raw = $null; $match = $null
$previousToken = [Environment]::GetEnvironmentVariable('GH_TOKEN','Process')
$previousHost = [Environment]::GetEnvironmentVariable('GH_HOST','Process')
$headers = @{ Authorization = 'Bearer ' + $token; Accept = 'application/vnd.github+json' }
function Set-LabVariable([string] $Name, [string] $Value) {
    $uri = "https://api.github.com/repos/$repository/actions/variables/$Name"
    $existing = Invoke-WebRequest -Uri $uri -Headers $headers -SkipHttpErrorCheck
    $body = @{ name = $Name; value = $Value } | ConvertTo-Json -Compress
    if ($existing.StatusCode -eq 200) {
        Invoke-RestMethod -Method Patch -Uri $uri -Headers $headers -ContentType 'application/json' -Body $body | Out-Null
    } elseif ($existing.StatusCode -eq 404) {
        Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$repository/actions/variables" -Headers $headers -ContentType 'application/json' -Body $body | Out-Null
    } else { throw "No se pudo consultar la variable $Name (HTTP $($existing.StatusCode))." }
}
function Set-LabSecret([string] $Name, [string] $Value) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $GitHubCli; $info.UseShellExecute = $false; $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    foreach ($argument in @('secret','set',$Name,'--repo',$repository)) { $info.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $info
    try {
        if (-not $process.Start()) { throw 'No se pudo ejecutar GitHub CLI.' }
        $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.Write($Value); $process.StandardInput.Close()
        $process.WaitForExit()
        $null = $stdout.GetAwaiter().GetResult(); $null = $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw "GitHub no confirmó el Secret $Name. La publicación queda deshabilitada." }
        Write-Host "Secret actualizado: $Name (valor oculto)."
    } finally { $process.Dispose(); $Value = $null }
}
try {
    [Environment]::SetEnvironmentVariable('GH_TOKEN',$token,'Process')
    [Environment]::SetEnvironmentVariable('GH_HOST','github.com','Process')
    # Keep new deployments off while the three temporary credentials are replaced.
    Set-LabVariable 'AWS_DEPLOY_ENABLED' 'false'
    foreach ($name in @('AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY','AWS_SESSION_TOKEN')) {
        $value = $session.$name | ConvertFrom-SecureString -AsPlainText
        try { Set-LabSecret $name $value } finally { $value = $null }
    }
    Set-LabVariable 'AWS_DEPLOY_CONFIG' (Get-Content -Raw -LiteralPath (Join-Path $local 'aws-deploy-config.json'))
    Set-LabVariable 'AWS_TASK_CONFIG' (Get-Content -Raw -LiteralPath (Join-Path $local 'aws-task-config.json'))
    if ($EnableDeployment) { Set-LabVariable 'AWS_DEPLOY_ENABLED' 'true' }
    Write-Host "Configuración del repositorio lista. Despliegue automático: $([bool]$EnableDeployment). Las credenciales siguen siendo temporales."
} finally {
    [Environment]::SetEnvironmentVariable('GH_TOKEN',$previousToken,'Process')
    [Environment]::SetEnvironmentVariable('GH_HOST',$previousHost,'Process')
    $headers = $null; $token = $null; $session = $null
}
