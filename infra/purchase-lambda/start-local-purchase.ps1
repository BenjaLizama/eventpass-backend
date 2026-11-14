#Requires -Version 7.0
[CmdletBinding()]
param([switch]$Build)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root = Join-Path $PSScriptRoot '../..'
$endpoint = 'http://127.0.0.1:4567'
$name = 'eventpass-purchase'
$functions = "$endpoint/2015-03-31/functions"
$mappings = "$endpoint/2015-03-31/event-source-mappings"
$archive = Join-Path ([IO.Path]::GetTempPath()) "eventpass-$([guid]::NewGuid()).zip"

function Send-Json($Uri, $Body, [hashtable]$Headers = @{}, $ContentType = 'application/json') {
    Invoke-RestMethod -Method Post -Uri $Uri -Headers $Headers -ContentType $ContentType `
        -Body ($Body | ConvertTo-Json -Depth 8 -Compress) -TimeoutSec 30
}

Push-Location $root
try {
    $buildArgs = if ($Build) { @('--build') } else { @() }
    docker compose -f docker-compose.yml -f infra/gateway/compose.private.yaml up -d --wait --wait-timeout 240 @buildArgs
    $schema = @'
BEGIN;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check,
    ADD CONSTRAINT orders_status_check CHECK (status IN ('PENDING','AWAITING_PAYMENT','PENDING_ISSUANCE','PAID','REJECTED_NO_CAPACITY','CANCELLED','EXPIRED'));
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_payment_status_check,
    ADD CONSTRAINT orders_payment_status_check CHECK (payment_status IN ('PENDING','APPROVED','REJECTED','REFUNDED'));
COMMIT;
'@
    $schema | docker compose exec -T postgres-orders sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
    $gatewayCompose = @('compose', '-p', 'eventpass-gateway', '-f', 'infra/gateway/compose.yaml',
        '-f', 'infra/purchase-lambda/compose.runtime.yaml')
    docker @gatewayCompose up -d floci-gateway
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        try { $null = Invoke-WebRequest "$endpoint/_floci/health" -TimeoutSec 5; break }
        catch { if ($attempt -eq 30) { throw }; Start-Sleep -Seconds 2 }
    }

    $api = & ./infra/gateway/start-local-gateway.ps1
    $config = docker compose config --format json | ConvertFrom-Json
    $auth = $config.services.'ms-auth'.environment
    $queue = Send-Json $endpoint @{ QueueName = 'eventpass-purchase-pending'; Attributes = @{ VisibilityTimeout = '120' } } `
        @{ 'X-Amz-Target' = 'AmazonSQS.CreateQueue' } 'application/x-amz-json-1.0'
    $attributes = Send-Json $endpoint @{ QueueUrl = $queue.QueueUrl; AttributeNames = @('QueueArn') } `
        @{ 'X-Amz-Target' = 'AmazonSQS.GetQueueAttributes' } 'application/x-amz-json-1.0'
    $existing = Invoke-WebRequest "$functions/$name" -SkipHttpErrorCheck
    if ($existing.StatusCode -eq 200) {
        $current = Invoke-RestMethod "${mappings}?FunctionName=$name"
        foreach ($mapping in $current.EventSourceMappings) {
            $null = Invoke-RestMethod -Method Delete "$mappings/$($mapping.UUID)"
        }
        $null = Invoke-RestMethod -Method Delete "$functions/$name"
    }
    elseif ($existing.StatusCode -ne 404) { throw "Lambda respondió HTTP $($existing.StatusCode)." }

    Compress-Archive -LiteralPath @((Join-Path $PSScriptRoot 'index.mjs'), (Join-Path $PSScriptRoot 'process-order.mjs')) -DestinationPath $archive
    $null = Send-Json $functions @{
        FunctionName = $name; Runtime = 'nodejs22.x'; Handler = 'index.handler'; Timeout = 20
        Role = 'arn:aws:iam::000000000000:role/eventpass-local'
        Code = @{ ZipFile = [Convert]::ToBase64String([IO.File]::ReadAllBytes($archive)) }
        Environment = @{ Variables = @{
            MS_ORDERS_CLIENT_ID = $auth.MS_ORDERS_CLIENT_ID
            MS_ORDERS_CLIENT_SECRET = $auth.MS_ORDERS_CLIENT_SECRET
        } }
    }
    $mapping = Send-Json $mappings @{ FunctionName = $name; EventSourceArn = $attributes.Attributes.QueueArn; BatchSize = 1 }
    docker compose -f docker-compose.yml -f infra/gateway/compose.private.yaml -f infra/purchase-lambda/compose.purchase.yaml up -d --wait --wait-timeout 240
    $env:EVENTPASS_API_ID = $api.ApiId
    docker @gatewayCompose -f infra/gateway/compose.swagger.yaml up -d
    for ($attempt = 1; $attempt -le 15; $attempt++) {
        try {
            $null = Invoke-WebRequest 'http://127.0.0.1:8090/api/v1/events?page=0&size=1' -TimeoutSec 10
            break
        }
        catch { if ($attempt -eq 15) { throw }; Start-Sleep -Seconds 2 }
    }
    [pscustomobject]@{ Swagger = 'http://127.0.0.1:8090/'; Gateway = $api.Url; EventSource = $mapping.UUID }
}
finally {
    Remove-Item -LiteralPath $archive -ErrorAction SilentlyContinue
    Pop-Location
}
