#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
$base = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$directory = Join-Path $base ('eventpass-runner-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$file = Join-Path $directory 'payload with spaces.json'
'{}' | Set-Content -LiteralPath $file
$oldExit = $global:LASTEXITCODE
$oldFunction = Get-Item Function:\aws -ErrorAction SilentlyContinue
function global:aws {
    $global:runnerArguments = @($args)
    $global:LASTEXITCODE = $global:runnerTestExit
    '{"StatusCode":200}'
}
try {
    $global:runnerTestExit = 0
    $result = & (Join-Path $PSScriptRoot 'Invoke-RunnerAws.ps1') -AwsArguments @('lambda','invoke','--payload','file:///input/request.json','/tmp/result.json') -InputFile $file | ConvertFrom-Json
    if ($result.StatusCode -ne 200 -or $global:runnerArguments -notcontains ('file://' + (Resolve-Path -LiteralPath $file).Path) -or
        $global:runnerArguments -notcontains 'us-east-1' -or $global:runnerArguments -notcontains '--no-cli-pager') { throw 'Traducción de archivo o región incorrecta.' }
    $global:runnerTestExit = 9
    $failed = $false
    try { & (Join-Path $PSScriptRoot 'Invoke-RunnerAws.ps1') -AwsArguments @('sts','get-caller-identity') | Out-Null } catch { $failed = $true }
    if (-not $failed) { throw 'Un error de AWS debe detener la prueba del runner.' }
    Write-Host 'OK: archivo con espacios, región, JSON y propagación del error de AWS en el runner.'
} finally {
    Remove-Item Function:\aws -ErrorAction SilentlyContinue
    if ($oldFunction) { Set-Item Function:\global:aws $oldFunction.ScriptBlock }
    $global:LASTEXITCODE = $oldExit
    $resolved = [IO.Path]::GetFullPath($directory)
    if (-not $resolved.StartsWith($base.TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar)) { throw 'Limpieza fuera del temporal.' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
