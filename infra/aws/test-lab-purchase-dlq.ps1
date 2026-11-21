#Requires -Version 7.0
[CmdletBinding()]
param([switch] $Restore)
$ErrorActionPreference = 'Stop'
$local = Join-Path $PSScriptRoot '../../.local'
$awsScript = Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'
$stateFile = Join-Path $local 'aws-dlq-test-state.json'
$baselineArchive = Join-Path $local 'purchase-dlq-original.zip'
$name = 'eventpass-process-purchase'
function Invoke-DlqAws([string[]] $Arguments, [string] $InputFile) {
    $value = & $awsScript -AwsArguments ($Arguments + @('--output','json')) -InputFile $InputFile
    if ($value) { $value | ConvertFrom-Json -Depth 50 }
}
function Get-CodeHash([string] $Path) {
    [Convert]::ToBase64String([Security.Cryptography.SHA256]::HashData([IO.File]::ReadAllBytes($Path)))
}
function Save-DlqState($State) { $State | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath $stateFile }
function Restore-PurchaseCode {
    $state = Get-Content -Raw -LiteralPath $stateFile | ConvertFrom-Json
    if (-not $state.RestoreNeeded) { return }
    if ((Get-CodeHash $baselineArchive) -ne $state.BaselineHash) { throw 'El ZIP de restauración no coincide con el original.' }
    $current = Invoke-DlqAws -Arguments @('lambda','get-function-configuration','--function-name',$name)
    if ($current.CodeSha256 -eq $state.BaselineHash) { $state.RestoreNeeded = $false; Save-DlqState $state; return }
    if ($current.CodeSha256 -ne $state.ProbeHash) { throw 'Lambda cambió fuera de la prueba. No se sobrescribirá esa versión; revisar y restaurar de forma coordinada.' }
    Invoke-DlqAws -Arguments @('lambda','update-function-code','--function-name',$name,'--revision-id',$current.RevisionId,
        '--zip-file','fileb:///input/request.json') -InputFile $baselineArchive | Out-Null
    Invoke-DlqAws -Arguments @('lambda','wait','function-updated-v2','--function-name',$name) | Out-Null
    $restored = Invoke-DlqAws -Arguments @('lambda','get-function-configuration','--function-name',$name)
    if ($restored.State -ne 'Active' -or $restored.LastUpdateStatus -ne 'Successful' -or $restored.CodeSha256 -ne $state.BaselineHash) { throw 'No se pudo verificar la restauración de Lambda.' }
    $state.RestoreNeeded = $false
    Save-DlqState $state
    Write-Host 'Código normal de Lambda restaurado y verificado por SHA-256.'
}
$identity = Invoke-DlqAws -Arguments @('sts','get-caller-identity')
if ($identity.Account -ne '119065066402') { throw 'Cuenta incorrecta.' }
if ($Restore) { Restore-PurchaseCode; return }
if (Test-Path -LiteralPath $stateFile) {
    $previous = Get-Content -Raw -LiteralPath $stateFile | ConvertFrom-Json
    if ($previous.RestoreNeeded) { throw 'Hay una prueba sin restaurar. Ejecuta este script con -Restore antes de continuar.' }
}
$current = Invoke-DlqAws -Arguments @('lambda','get-function-configuration','--function-name',$name)
if ($current.State -ne 'Active' -or $current.LastUpdateStatus -ne 'Successful') { throw 'Lambda no está estable.' }
$original = Join-Path $local 'purchase.zip'
if ((Get-CodeHash $original) -ne $current.CodeSha256) { throw 'El ZIP local no coincide con el código activo. No se modificará Lambda.' }
$deployment = Get-Content -Raw -LiteralPath (Join-Path $local 'aws-purchase-deployment.json') | ConvertFrom-Json
$mapping = Invoke-DlqAws -Arguments @('lambda','get-event-source-mapping','--uuid',$deployment.MappingId)
if ($mapping.State -ne 'Enabled' -or $mapping.BatchSize -ne 1 -or $mapping.FunctionArn -ne $current.FunctionArn) { throw 'El vínculo SQS no coincide con el despliegue esperado.' }
$queue = Invoke-DlqAws -Arguments @('sqs','get-queue-attributes','--queue-url',$deployment.QueueUrl,'--attribute-names','All')
$policy = $queue.Attributes.RedrivePolicy | ConvertFrom-Json
if ($policy.maxReceiveCount -ne 5 -or $queue.Attributes.VisibilityTimeout -ne '120' -or
    $policy.deadLetterTargetArn -ne 'arn:aws:sqs:us-east-1:119065066402:eventpass-purchase-errors') { throw 'La prueba requiere la política habitual: cinco intentos y visibilidad 120.' }
$deadLetter = (Invoke-DlqAws -Arguments @('sqs','get-queue-url','--queue-name','eventpass-purchase-errors')).QueueUrl
$network = Get-Content -Raw -LiteralPath (Join-Path $local 'aws-network-outputs.json') | ConvertFrom-Json
$gateway = ($network | Where-Object OutputKey -eq GatewayUrl).OutputValue.TrimEnd('/')
if ($gateway -notmatch '^https://[a-z0-9]+\.execute-api\.us-east-1\.amazonaws\.com$') { throw 'Gateway inesperado.' }
$settings = Get-Content -Raw -LiteralPath (Join-Path $local 'aws-task-config.json') | ConvertFrom-Json
$tokens = [Collections.Generic.List[string]]::new()
$checks = [Collections.Generic.List[object]]::new()
$adminToken = ''; $customerToken = ''; $eventId = ''; $completed = $false
function Assert-Dlq($Condition, [string] $Message) { if (-not $Condition) { throw $Message } }
function Invoke-DlqProbe([string] $Method, [string] $Path, [int] $Expected, $Body = $null, [string] $Token = '') {
    $parameters = @{ Uri = $gateway + $Path; Method = $Method; SkipHttpErrorCheck = $true; TimeoutSec = 30; MaximumRedirection = 0; Headers = @{} }
    if ($Token) { $parameters.Headers.Authorization = 'Bearer ' + $Token }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 12 -Compress; $parameters.ContentType = 'application/json' }
    $response = Invoke-WebRequest @parameters
    $checks.Add(@{ Method = $Method; Path = $Path; Status = [int]$response.StatusCode; Expected = $Expected })
    if ([int]$response.StatusCode -ne $Expected) { throw "$Method $Path respondió HTTP $($response.StatusCode); se esperaba $Expected." }
    Write-Host "$Method $Path -> HTTP $($response.StatusCode)"
    if ($response.Content) { return ($response.Content | ConvertFrom-Json -Depth 30).data }
}
try {
    $secretArn = $settings.secrets.ADMIN_PASSWORD -replace ':password::$',''
    $secret = Invoke-DlqAws -Arguments @('secretsmanager','get-secret-value','--secret-id',$secretArn)
    $password = ($secret.SecretString | ConvertFrom-Json).password; $secret = $null
    $admin = Invoke-DlqProbe 'POST' '/api/v1/auth/login' 200 @{ email = $settings.environment.ADMIN_EMAIL; password = $password }
    $password = $null; $adminToken = $admin.access_token; $tokens.Add($adminToken)
    $runId = [guid]::NewGuid().ToString('N')
    $venue = Invoke-DlqProbe 'POST' '/api/v1/venues' 201 @{ name = "Recuperación AWS $runId"; address = 'Calle de prueba 123'; city = 'Santiago'; country = 'Chile'; capacity = 1 } $adminToken
    $start = [DateTimeOffset]::UtcNow.AddDays(7)
    $event = Invoke-DlqProbe 'POST' '/api/v1/events' 201 @{
        title = "DLQ AWS $runId"; description = 'Fallo persistente y recuperación'; category = 'CONCERT';
        startDate = $start.ToString('o'); endDate = $start.AddHours(2).ToString('o'); venueId = $venue.id;
        ticketCategories = @(@{ name = 'General'; price = 10000; totalCapacity = 1; maxPerUser = 1 })
    } $adminToken
    $eventId = $event.id; $categoryId = $event.ticketCategories[0].id
    Invoke-DlqProbe 'PATCH' "/api/v1/events/$eventId/publish" 200 $null $adminToken | Out-Null
    $password = [guid]::NewGuid().ToString('N')
    $customer = Invoke-DlqProbe 'POST' '/api/v1/auth/register' 201 @{ email = "dlq-$runId@example.com"; password = $password }
    $password = $null; $customerToken = $customer.access_token; $tokens.Add($customerToken)
    $order = Invoke-DlqProbe 'POST' '/api/v1/orders' 201 @{ eventId = $eventId; ticketCategoryId = $categoryId; quantity = 1 } $customerToken
    Assert-Dlq ($order.status -eq 'AWAITING_PAYMENT') 'La orden no está esperando el pago simulado.'
    $directory = Join-Path $local 'dlq-probe-package'
    & node (Join-Path $PSScriptRoot 'dlq-probe.mjs') $order.id $directory
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo preparar el fallo aislado.' }
    $probeArchive = Join-Path $local 'purchase-dlq-probe.zip'
    Compress-Archive -LiteralPath @((Join-Path $directory 'index.mjs'), (Join-Path $directory 'purchase-handler.mjs'),
        (Join-Path $directory 'process-order.mjs'), (Join-Path $directory 'dlq-probe.mjs')) -DestinationPath $probeArchive -Force
    Copy-Item -LiteralPath $original -Destination $baselineArchive -Force
    $state = @{ RestoreNeeded = $true; BaselineHash = $current.CodeSha256; ProbeHash = Get-CodeHash $probeArchive;
        OrderId = $order.id; EventId = $eventId; StartedAt = [DateTimeOffset]::UtcNow.ToString('o') }
    Save-DlqState $state
    Invoke-DlqAws -Arguments @('lambda','update-function-code','--function-name',$name,'--revision-id',$current.RevisionId,
        '--zip-file','fileb:///input/request.json') -InputFile $probeArchive | Out-Null
    Invoke-DlqAws -Arguments @('lambda','wait','function-updated-v2','--function-name',$name) | Out-Null
    $paid = Invoke-DlqProbe 'POST' "/api/v1/orders/$($order.id)/payment" 200 @{ paymentMethod = 'CARD' } $customerToken
    Assert-Dlq ($paid.status -eq 'PENDING_ISSUANCE' -and $paid.paymentStatus -eq 'APPROVED') 'El pago simulado no quedó pendiente de emisión.'
    $found = $false
    $deadline = [DateTimeOffset]::UtcNow.AddMinutes(25)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        $attributes = (Invoke-DlqAws -Arguments @('sqs','get-queue-attributes','--queue-url',$deadLetter,'--attribute-names','ApproximateNumberOfMessages')).Attributes
        if ([int]$attributes.ApproximateNumberOfMessages -gt 0) {
            $batch = Invoke-DlqAws -Arguments @('sqs','receive-message','--queue-url',$deadLetter,'--max-number-of-messages','10','--visibility-timeout','30','--wait-time-seconds','1')
            try {
                foreach ($message in $batch.Messages) {
                    try { $body = $message.Body | ConvertFrom-Json -Depth 15 } catch { continue }
                    if ($body.orderId -eq $order.id) { $found = $true }
                }
            } finally {
                foreach ($message in $batch.Messages) {
                    Invoke-DlqAws -Arguments @('sqs','change-message-visibility','--queue-url',$deadLetter,'--receipt-handle',$message.ReceiptHandle,'--visibility-timeout','0') | Out-Null
                }
            }
            if ($found) { break }
        }
        Write-Host "Esperando que la orden $($order.id) agote sus cinco intentos y llegue a la DLQ."
        Start-Sleep -Seconds 25
    }
    Assert-Dlq $found 'La orden no llegó a la cola de errores dentro del tiempo previsto.'
    $pending = Invoke-DlqProbe 'GET' "/api/v1/orders/$($order.id)" 200 $null $customerToken
    Assert-Dlq ($pending.status -eq 'PENDING_ISSUANCE' -and $pending.paymentStatus -eq 'APPROVED') 'Un fallo técnico no debe finalizar ni devolver el pago como si faltara cupo.'
    $category = Invoke-DlqProbe 'GET' "/api/v1/events/$eventId/ticket-categories/$categoryId" 200
    Assert-Dlq ($category.availableCapacity -eq 0) 'El cupo reservado debe conservarse durante los reintentos.'
    $tickets = @(Invoke-DlqProbe 'GET' '/api/v1/tickets/me' 200 $null $customerToken | Where-Object orderId -eq $order.id)
    Assert-Dlq ($tickets.Count -eq 0) 'No debe existir ticket antes de recuperar la emisión.'
    $logCount = Invoke-DlqAws -Arguments @('logs','filter-log-events','--log-group-name',"/aws/lambda/$name",
        '--filter-pattern',('"EVENTPASS_DLQ_PROBE ' + $order.id + '"'),'--query','length(events)')
    Assert-Dlq ($logCount -ge 5) 'Falta evidencia de los fallos repetidos en CloudWatch.'
    Restore-PurchaseCode
    $recovery = & (Join-Path $PSScriptRoot 'recover-lab-purchase.ps1') -OrderId $order.id
    Assert-Dlq $recovery.Forwarded 'La recuperación no confirmó el reenvío.'
    $finished = $null
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        $finished = Invoke-DlqProbe 'GET' "/api/v1/orders/$($order.id)" 200 $null $customerToken
        if ($finished.status -eq 'PAID') { break }
        Assert-Dlq ($finished.status -eq 'PENDING_ISSUANCE') 'La compra recuperada cambió a un estado inesperado.'
        Start-Sleep -Seconds 3
    }
    Assert-Dlq ($finished.status -eq 'PAID' -and $finished.paymentStatus -eq 'APPROVED') 'La compra recuperada no terminó pagada.'
    $tickets = @(Invoke-DlqProbe 'GET' '/api/v1/tickets/me' 200 $null $customerToken | Where-Object orderId -eq $order.id)
    Assert-Dlq ($tickets.Count -eq 1) 'La recuperación debe generar exactamente un ticket.'
    $category = Invoke-DlqProbe 'GET' "/api/v1/events/$eventId/ticket-categories/$categoryId" 200
    Assert-Dlq ($category.availableCapacity -eq 0) 'La recuperación no debe volver a descontar aforo.'
    $completed = $true
    @{ OrderId = $order.id; EventId = $eventId; FaultLogCount = $logCount; RedriveAttempts = 5; VisibilityTimeout = 120;
        FoundInDeadLetterQueue = $true; Recovered = $true; Tickets = $tickets.Count; AvailableCapacity = $category.availableCapacity;
        BaselineHash = $state.BaselineHash; Checks = $checks } | ConvertTo-Json -Depth 12 |
        Set-Content -LiteralPath (Join-Path $local 'aws-dlq-smoke.json')
    Write-Host 'OK: cinco intentos, DLQ real, restauración del código, recuperación de la orden y un solo ticket con un solo cupo reservado.'
} finally {
    $password = $null
    $restoreFailure = $null
    if (Test-Path -LiteralPath $stateFile) {
        try { Restore-PurchaseCode } catch { $restoreFailure = $_ }
    }
    if ($completed -and $eventId -and $adminToken) {
        try { Invoke-DlqProbe 'PATCH' "/api/v1/events/$eventId/cancel" 200 $null $adminToken | Out-Null } catch { Write-Warning 'Revisar la cancelación del evento de prueba.' }
    }
    foreach ($token in $tokens) { try { Invoke-DlqProbe 'POST' '/api/v1/auth/logout' 200 $null $token | Out-Null } catch { Write-Warning 'Revisar cierre de sesión de prueba.' } }
    if ($restoreFailure) { throw $restoreFailure }
}
