#Requires -Version 7.0
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$endpoint = 'http://127.0.0.1:4568'
$functionUri = "$endpoint/2015-03-31/functions/eventpass-lambda-smoke"
$mappingUri = "$endpoint/2015-03-31/event-source-mappings"
$resultBucket = "$endpoint/eventpass-lambda-smoke"
$project = "eventpass-lambda-smoke-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$compose = @('compose', '-p', $project, '-f', (Join-Path $PSScriptRoot 'compose.yaml'))
$archive = Join-Path ([IO.Path]::GetTempPath()) "eventpass-lambda-$([guid]::NewGuid()).zip"
$mappingId = $null
$functionCreated = $false

function Invoke-Sqs($Operation, $Payload) {
    $request = @{
        Uri = $endpoint
        Method = 'Post'
        TimeoutSec = 15
        Headers = @{ 'X-Amz-Target' = "AmazonSQS.$Operation" }
        ContentType = 'application/x-amz-json-1.0'
        Body = $Payload | ConvertTo-Json -Depth 5 -Compress
    }
    Invoke-RestMethod @request
}

function Invoke-LambdaPost($Uri, $Payload) {
    $request = @{
        Uri = $Uri
        Method = 'Post'
        TimeoutSec = 30
        ContentType = 'application/json'
        Body = $Payload | ConvertTo-Json -Depth 5 -Compress
    }
    Invoke-RestMethod @request
}

try {
    docker @compose up -d
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    do {
        try {
            $health = Invoke-WebRequest -Uri "$endpoint/_floci/health" -TimeoutSec 3 -SkipHttpErrorCheck
            if ($health.StatusCode -eq 200) {
                break
            }
        }
        catch {
            if ([DateTime]::UtcNow -ge $deadline) { throw }
        }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($health.StatusCode -ne 200) {
        throw 'Floci no arrancó a tiempo.'
    }

    $null = Invoke-WebRequest -Uri $resultBucket -Method Put -TimeoutSec 10
    $queue = Invoke-Sqs CreateQueue @{
        QueueName = 'eventpass-lambda-smoke'
        Attributes = @{ VisibilityTimeout = '12' }
    }
    $attributes = Invoke-Sqs GetQueueAttributes @{
        QueueUrl = $queue.QueueUrl
        AttributeNames = @('QueueArn')
    }

    Compress-Archive -LiteralPath (Join-Path $PSScriptRoot 'index.mjs') -DestinationPath $archive
    $null = Invoke-LambdaPost "$endpoint/2015-03-31/functions" @{
        FunctionName = 'eventpass-lambda-smoke'
        Runtime = 'nodejs22.x'
        Role = 'arn:aws:iam::000000000000:role/eventpass-lambda-smoke'
        Handler = 'index.handler'
        Timeout = 2
        Code = @{ ZipFile = [Convert]::ToBase64String([IO.File]::ReadAllBytes($archive)) }
        Environment = @{ Variables = @{ FLOCI_SMOKE_ENDPOINT = 'http://floci:4566' } }
    }
    $functionCreated = $true
    $mapping = Invoke-LambdaPost $mappingUri @{
        FunctionName = 'eventpass-lambda-smoke'
        EventSourceArn = $attributes.Attributes.QueueArn
        BatchSize = 1
    }
    $mappingId = $mapping.UUID

    $orderId = [guid]::NewGuid().ToString()
    $sent = Invoke-Sqs SendMessage @{
        QueueUrl = $queue.QueueUrl
        MessageBody = (@{ orderId = $orderId } | ConvertTo-Json -Compress)
    }
    $deadline = [DateTime]::UtcNow.AddMinutes(3)
    do {
        $response = Invoke-WebRequest -Uri "$resultBucket/$orderId.json" -TimeoutSec 10 -SkipHttpErrorCheck
        if ($response.StatusCode -eq 200) {
            $result = $response.Content | ConvertFrom-Json
            if ($result.orderId -ne $orderId -or $result.messageId -ne $sent.MessageId -or $result.receiveCount -lt 2) {
                throw 'Lambda no confirmó la segunda entrega del mensaje esperado.'
            }
            Write-Output "SQS invocó Lambda tras reintentar el mensaje $($result.messageId). Entrega $($result.receiveCount)."
            return
        }
        if ($response.StatusCode -ne 404) {
            throw "S3 devolvió HTTP $($response.StatusCode) al consultar el resultado."
        }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)

    throw 'Lambda no registró la entrega en S3 antes del plazo.'
}
catch {
    docker @compose logs --tail 50
    throw
}
finally {
    try {
        if ($mappingId) {
            $null = Invoke-RestMethod -Method Delete -Uri "$mappingUri/$mappingId" -TimeoutSec 10
        }
        if ($functionCreated) {
            $null = Invoke-RestMethod -Method Delete -Uri $functionUri -TimeoutSec 10
        }
    }
    finally {
        try {
            docker @compose down
        }
        finally {
            Remove-Item -LiteralPath $archive -ErrorAction SilentlyContinue
        }
    }
}
