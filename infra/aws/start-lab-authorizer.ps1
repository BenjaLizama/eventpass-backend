#Requires -Version 7.0
[CmdletBinding()]
param([string] $AwsScript = (Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'))
$ErrorActionPreference = 'Stop'
$local = Join-Path $PSScriptRoot '../../.local'
New-Item -ItemType Directory -Force $local | Out-Null
function Invoke-AuthorizerAws([string[]] $Arguments, [string] $InputFile) {
    $text = & $AwsScript -AwsArguments ($Arguments + @('--output','json')) -InputFile $InputFile
    if ($text) { $text | ConvertFrom-Json -Depth 40 }
}
if ((Invoke-AuthorizerAws -Arguments @('sts','get-caller-identity')).Account -ne '119065066402') { throw 'Cuenta incorrecta.' }
$network = (Invoke-AuthorizerAws -Arguments @('cloudformation','describe-stacks','--stack-name','eventpass-network')).Stacks[0]
if ($network.StackStatus -notin @('CREATE_COMPLETE','UPDATE_COMPLETE')) { throw 'La red no está estable.' }
$ids = @{}; foreach ($item in $network.Outputs) { $ids[$item.OutputKey] = $item.OutputValue }
foreach ($required in @('AuthServiceUrl','AuthorizerSecurityGroup','AppA','AppB')) {
    if (-not $ids[$required]) { throw 'Aplica primero la red actualizada con start-lab-network.ps1 -Apply.' }
}
$repository = 'eventpass/gateway-authorizer'
$name = 'eventpass-gateway-authorizer'
$registry = '119065066402.dkr.ecr.us-east-1.amazonaws.com'
$arn = "arn:aws:lambda:us-east-1:119065066402:function:$name"
$repos = Invoke-AuthorizerAws -Arguments @('ecr','describe-repositories','--query','repositories[].repositoryName')
if ($repository -notin $repos) {
    Invoke-AuthorizerAws -Arguments @('ecr','create-repository','--repository-name',$repository,'--image-tag-mutability','IMMUTABLE') | Out-Null
}
# Only this Lambda may retrieve this repository's images through the Lambda service.
$policy = @{Version='2012-10-17';Statement=@(@{Sid='LambdaECRImageRetrievalPolicy';Effect='Allow';Principal=@{Service='lambda.amazonaws.com'};Action=@('ecr:BatchGetImage','ecr:GetDownloadUrlForLayer');Condition=@{ArnLike=@{'aws:sourceArn'=$arn}}})}
$file = Join-Path $local 'authorizer-ecr-policy.json'
$policy | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath $file
Invoke-AuthorizerAws -Arguments @('ecr','set-repository-policy','--repository-name',$repository,'--policy-text','file:///input/request.json') -InputFile $file | Out-Null
$password = & $AwsScript -AwsArguments @('ecr','get-login-password')
try { $password | docker login --username AWS --password-stdin $registry | Out-Host; if ($LASTEXITCODE -ne 0) { throw 'Login ECR falló.' } } finally { $password=$null }
$sha = & git -c safe.directory='*' rev-parse HEAD
$tag = "${registry}/${repository}:$sha-$([guid]::NewGuid().ToString('N'))"
docker build --platform linux/amd64 --provenance=false --label "org.opencontainers.image.revision=$sha" -t $tag (Join-Path $PSScriptRoot '../gateway-authorizer') | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Build/pruebas del autorizador fallaron.' }
docker push $tag | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Publicación del autorizador falló.' }
$digest = (Invoke-AuthorizerAws -Arguments @('ecr','describe-images','--repository-name',$repository,'--image-ids',"imageTag=$($tag.Split(':')[-1])")).imageDetails[0].imageDigest
if ($digest -notmatch '^sha256:[a-f0-9]{64}$') { throw 'Digest no válido.' }
$image = "${registry}/${repository}@$digest"
$existing = Invoke-AuthorizerAws -Arguments @('lambda','list-functions','--query',"Functions[?FunctionName=='$name'].FunctionArn | [0]")
$configuration = @{
    FunctionName=$name; Role='arn:aws:iam::119065066402:role/LabRole'; Timeout=10; MemorySize=512
    Environment=@{Variables=@{AUTH_SERVICE_URL=$ids.AuthServiceUrl}}
    VpcConfig=@{SubnetIds=@($ids.AppA,$ids.AppB);SecurityGroupIds=@($ids.AuthorizerSecurityGroup)}
}
if (-not $existing) {
    $configuration.PackageType='Image'; $configuration.Code=@{ImageUri=$image}; $configuration.Architectures=@('x86_64')
    $file = Join-Path $local 'authorizer-create.json'
    $configuration | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath $file
    Invoke-AuthorizerAws -Arguments @('lambda','create-function','--cli-input-json','file:///input/request.json') -InputFile $file | Out-Null
    Invoke-AuthorizerAws -Arguments @('lambda','wait','function-active-v2','--function-name',$name) | Out-Null
} else {
    Invoke-AuthorizerAws -Arguments @('lambda','update-function-code','--function-name',$name,'--image-uri',$image) | Out-Null
    Invoke-AuthorizerAws -Arguments @('lambda','wait','function-updated-v2','--function-name',$name) | Out-Null
    $file = Join-Path $local 'authorizer-config.json'
    $configuration | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath $file
    Invoke-AuthorizerAws -Arguments @('lambda','update-function-configuration','--cli-input-json','file:///input/request.json') -InputFile $file | Out-Null
    Invoke-AuthorizerAws -Arguments @('lambda','wait','function-updated-v2','--function-name',$name) | Out-Null
}
$group = "/aws/lambda/$name"
$groups = Invoke-AuthorizerAws -Arguments @('logs','describe-log-groups','--log-group-name-prefix',$group)
if ($group -notin $groups.logGroups.logGroupName) { Invoke-AuthorizerAws -Arguments @('logs','create-log-group','--log-group-name',$group) | Out-Null }
Invoke-AuthorizerAws -Arguments @('logs','put-retention-policy','--log-group-name',$group,'--retention-in-days','7') | Out-Null
@{FunctionArn=$arn;ImageUri=$image;Revision=$sha} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $local 'aws-authorizer-deployment.json')
Write-Host 'Autorizador Java/Docker disponible. Conectar las rutas con start-lab-network.ps1 -Apply.'
