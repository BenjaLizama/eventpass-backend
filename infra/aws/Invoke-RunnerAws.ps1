#Requires -Version 7.0
[CmdletBinding()]
param([Parameter(Mandatory)][string[]] $AwsArguments, [string] $InputFile)
$ErrorActionPreference = 'Stop'
$arguments = @($AwsArguments)
if ($InputFile) {
    $path = (Resolve-Path -LiteralPath $InputFile).Path
    $arguments = @($arguments | ForEach-Object {
        if ($_ -eq 'file:///input/request.json') { 'file://' + $path }
        elseif ($_ -eq 'fileb:///input/request.json') { 'fileb://' + $path }
        else { $_ }
    })
}
& aws @arguments --region us-east-1 --no-cli-pager
if ($LASTEXITCODE -ne 0) { throw 'La llamada AWS del runner falló. Revisar permisos y caducidad de las credenciales.' }
