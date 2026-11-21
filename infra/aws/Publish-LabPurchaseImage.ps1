#Requires -Version 7.0
[CmdletBinding()]
param(
    [string] $ContextPath = (Join-Path $PSScriptRoot '../purchase-lambda'),
    [ValidatePattern('^[a-z0-9-]+$')][string] $TagPrefix = 'purchase'
)
$ErrorActionPreference = 'Stop'
$awsScript = Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'
$local = Join-Path $PSScriptRoot '../../.local'
$registry = '119065066402.dkr.ecr.us-east-1.amazonaws.com'
$repository = 'eventpass/purchase-lambda'
function Invoke-ImageAws([string[]] $Arguments, [string] $InputFile) {
    $value = & $awsScript -AwsArguments ($Arguments + @('--output','json')) -InputFile $InputFile
    if ($value) { $value | ConvertFrom-Json -Depth 30 }
}
$identity = Invoke-ImageAws -Arguments @('sts','get-caller-identity')
if ($identity.Account -ne '119065066402') { throw 'Cuenta incorrecta.' }
$repositories = Invoke-ImageAws -Arguments @('ecr','describe-repositories','--query','repositories[].repositoryName')
if ($repository -notin $repositories) {
    Invoke-ImageAws -Arguments @('ecr','create-repository','--repository-name',$repository,'--image-tag-mutability','IMMUTABLE',
        '--encryption-configuration','encryptionType=AES256') | Out-Null
    $policy = @{ Version = '2012-10-17'; Statement = @(@{
        Sid = 'LambdaECRImageRetrievalPolicy'; Effect = 'Allow'; Principal = @{ Service = 'lambda.amazonaws.com' };
        Action = @('ecr:BatchGetImage','ecr:GetDownloadUrlForLayer');
        Condition = @{ ArnLike = @{ 'aws:sourceArn' = 'arn:aws:lambda:us-east-1:119065066402:function:eventpass-process-purchase-image' } }
    }) }
    $file = Join-Path $local 'purchase-image-policy.json'
    $policy | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $file
    Invoke-ImageAws -Arguments @('ecr','set-repository-policy','--repository-name',$repository,'--policy-text','file:///input/request.json') -InputFile $file | Out-Null
}
$password = & $awsScript -AwsArguments @('ecr','get-login-password')
try {
    $password | & docker login --username AWS --password-stdin $registry | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo autenticar Docker en ECR.' }
} finally { $password = $null }
$revision = & git -c safe.directory='*' rev-parse HEAD
if ($revision -notmatch '^[a-f0-9]{40}$') { throw 'Commit inválido.' }
$tag = $TagPrefix + '-' + $revision + '-' + [guid]::NewGuid().ToString('N')
$image = "${registry}/${repository}:$tag"
& docker build --platform linux/amd64 --provenance=false --label "org.opencontainers.image.revision=$revision" -t $image $ContextPath | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'No se pudo construir la imagen de Lambda.' }
& docker push $image | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'No se pudo publicar la imagen de Lambda.' }
$info = Invoke-ImageAws -Arguments @('ecr','describe-images','--repository-name',$repository,'--image-ids',"imageTag=$tag")
$digest = $info.imageDetails[0].imageDigest
if ($digest -notmatch '^sha256:[a-f0-9]{64}$') { throw 'Digest de Lambda inválido.' }
[pscustomobject]@{ ImageUri = "${registry}/${repository}@$digest"; Revision = $revision }
