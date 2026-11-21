#Requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$local = Join-Path $PSScriptRoot '../../.local'
$awsScript = Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'
$network = Get-Content -Raw -LiteralPath (Join-Path $local 'aws-network-outputs.json') | ConvertFrom-Json
$gateway = ($network | Where-Object OutputKey -eq 'GatewayUrl').OutputValue.TrimEnd('/')
if ($gateway -notmatch '^https://[a-z0-9]+\.execute-api\.us-east-1\.amazonaws\.com$') { throw 'Gateway inesperado.' }
$settings = Get-Content -Raw -LiteralPath (Join-Path $local 'aws-task-config.json') | ConvertFrom-Json
$deployment = Get-Content -Raw -LiteralPath (Join-Path $local 'aws-purchase-deployment.json') | ConvertFrom-Json
$checks = [Collections.Generic.List[object]]::new()
$tokens = [Collections.Generic.List[string]]::new()
$eventId = ''
$adminToken = ''
$completed = $false
function Assert-Purchase($Condition, [string] $Message) { if (-not $Condition) { throw $Message } }
function Invoke-PurchaseProbe([string] $Method, [string] $Path, [int] $Expected, $Body = $null, [string] $Token = '') {
    $parameters = @{ Uri = $gateway + $Path; Method = $Method; SkipHttpErrorCheck = $true;
        TimeoutSec = 30; MaximumRedirection = 0; Headers = @{} }
    if ($Token) { $parameters.Headers.Authorization = 'Bearer ' + $Token }
    if ($null -ne $Body) { $parameters.Body = $Body | ConvertTo-Json -Depth 15 -Compress; $parameters.ContentType = 'application/json' }
    $response = Invoke-WebRequest @parameters
    $safePath = $Path -replace '/tickets/(?!me(?:/|$))[^/]+','/tickets/{code}'
    $checks.Add(@{ Method = $Method; Path = $safePath;
        Status = [int]$response.StatusCode; Expected = $Expected })
    if ([int]$response.StatusCode -ne $Expected) { throw "$Method respondió HTTP $($response.StatusCode); se esperaba $Expected. Revisar el servicio." }
    # Ticket codes, credentials and JWTs are never printed or written to reports.
    Write-Host "$Method $safePath -> HTTP $($response.StatusCode)"
    if ($response.Content) { return ($response.Content | ConvertFrom-Json -Depth 30).data }
}
function Wait-Purchase($Order, [string] $Token) {
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        $current = Invoke-PurchaseProbe 'GET' "/api/v1/orders/$($Order.id)" 200 $null $Token
        if ($current.status -in @('PAID','REJECTED_NO_CAPACITY')) { return $current }
        Assert-Purchase ($current.status -eq 'PENDING_ISSUANCE') 'Estado inesperado mientras Lambda procesa la compra.'
        Start-Sleep -Seconds 3
    }
    throw 'La compra no terminó a tiempo. Revisar Lambda, SQS y la cola de errores.'
}
try {
    $identity = & $awsScript -AwsArguments @('sts','get-caller-identity','--output','json') | ConvertFrom-Json
    Assert-Purchase ($identity.Account -eq '119065066402') 'Cuenta AWS incorrecta.'
    Assert-Purchase ($deployment.State -eq 'Enabled') 'Falta el vínculo SQS habilitado.'
    $secretArn = $settings.secrets.ADMIN_PASSWORD -replace ':password::$',''
    $secret = & $awsScript -AwsArguments @('secretsmanager','get-secret-value','--secret-id',$secretArn,'--output','json') | ConvertFrom-Json
    $password = ($secret.SecretString | ConvertFrom-Json).password
    $secret = $null
    $admin = Invoke-PurchaseProbe 'POST' '/api/v1/auth/login' 200 @{ email = $settings.environment.ADMIN_EMAIL; password = $password }
    $password = $null
    $adminToken = $admin.access_token
    $tokens.Add($adminToken)
    $runId = [guid]::NewGuid().ToString('N')
    $venue = Invoke-PurchaseProbe 'POST' '/api/v1/venues' 201 @{ name = "Compra AWS $runId"; address = 'Calle de prueba 123'; city = 'Santiago'; country = 'Chile'; capacity = 1 } $adminToken
    $start = [DateTimeOffset]::UtcNow.AddDays(7)
    $event = Invoke-PurchaseProbe 'POST' '/api/v1/events' 201 @{
        title = "Compra AWS $runId"; description = 'Compra asíncrona de prueba'; category = 'CONCERT';
        startDate = $start.ToString('o'); endDate = $start.AddHours(2).ToString('o'); venueId = $venue.id;
        ticketCategories = @(@{ name = 'General'; price = 10000; totalCapacity = 1; maxPerUser = 1 })
    } $adminToken
    $eventId = $event.id
    $categoryId = $event.ticketCategories[0].id
    Invoke-PurchaseProbe 'PATCH' "/api/v1/events/$eventId/publish" 200 $null $adminToken | Out-Null
    $buyers = @()
    foreach ($index in 1..2) {
        $password = [guid]::NewGuid().ToString('N')
        $registered = Invoke-PurchaseProbe 'POST' '/api/v1/auth/register' 201 @{ email = "purchase-$runId-$index@example.com"; password = $password }
        $password = $null
        $token = $registered.access_token
        $tokens.Add($token)
        $me = Invoke-PurchaseProbe 'GET' '/api/v1/auth/me' 200 $null $token
        $order = Invoke-PurchaseProbe 'POST' '/api/v1/orders' 201 @{ eventId = $eventId; ticketCategoryId = $categoryId; quantity = 1 } $token
        Assert-Purchase ($order.status -eq 'AWAITING_PAYMENT' -and [decimal]$order.totalAmount -eq 10000) 'Orden inicial o importe incorrecto.'
        $buyers += @{ Token = $token; UserId = $me.id; Order = $order }
    }
    $category = Invoke-PurchaseProbe 'GET' "/api/v1/events/$eventId/ticket-categories/$categoryId" 200
    Assert-Purchase ($category.availableCapacity -eq 1) 'Crear órdenes sin pago alteró el aforo.'
    foreach ($buyer in $buyers) {
        $paid = Invoke-PurchaseProbe 'POST' "/api/v1/orders/$($buyer.Order.id)/payment" 200 @{ paymentMethod = 'CARD' } $buyer.Token
        Assert-Purchase ($paid.status -eq 'PENDING_ISSUANCE' -and $paid.paymentStatus -eq 'APPROVED') 'El pago simulado no pasó a la cola.'
    }
    foreach ($buyer in $buyers) { $buyer.Result = Wait-Purchase $buyer.Order $buyer.Token }
    $winner = @($buyers | Where-Object { $_.Result.status -eq 'PAID' })
    $loser = @($buyers | Where-Object { $_.Result.status -eq 'REJECTED_NO_CAPACITY' })
    Assert-Purchase ($winner.Count -eq 1 -and $loser.Count -eq 1) 'Un solo comprador debe obtener el único cupo.'
    $winner = $winner[0]; $loser = $loser[0]
    Assert-Purchase ($winner.Result.paymentStatus -eq 'APPROVED' -and $loser.Result.paymentStatus -eq 'REFUNDED') 'La compra rechazada debe quedar con devolución simulada.'
    $mine = @(Invoke-PurchaseProbe 'GET' '/api/v1/tickets/me' 200 $null $winner.Token | Where-Object orderId -eq $winner.Order.id)
    $none = @(Invoke-PurchaseProbe 'GET' '/api/v1/tickets/me' 200 $null $loser.Token | Where-Object orderId -eq $loser.Order.id)
    Assert-Purchase ($mine.Count -eq 1 -and $none.Count -eq 0) 'La emisión no coincide con el aforo.'
    $ticket = $mine[0]
    $category = Invoke-PurchaseProbe 'GET' "/api/v1/events/$eventId/ticket-categories/$categoryId" 200
    Assert-Purchase ($category.availableCapacity -eq 0) 'El único cupo debe estar reservado.'
    foreach ($buyer in @($winner,$loser)) {
        $item = $buyer.Order.items[0]
        $message = @{ orderId = $buyer.Order.id; userId = $buyer.UserId; items = @(@{
            orderItemId = $item.id; eventId = $eventId; ticketCategoryId = $categoryId; quantity = 1
        }) } | ConvertTo-Json -Depth 10 -Compress
        $replay = @{ Records = @(@{ body = $message }) }
        $file = Join-Path $local 'purchase-replay.json'
        $replay | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $file
        $invoked = & $awsScript -InputFile $file -AwsArguments @('lambda','invoke','--function-name',$deployment.FunctionArn,
            '--payload','file:///input/request.json','--cli-binary-format','raw-in-base64-out','/tmp/purchase-response.json','--output','json') | ConvertFrom-Json
        Assert-Purchase (-not $invoked.FunctionError -and $invoked.StatusCode -eq 200) 'Falló la repetición de una entrega de Lambda.'
        $again = Invoke-PurchaseProbe 'GET' "/api/v1/orders/$($buyer.Order.id)" 200 $null $buyer.Token
        Assert-Purchase ($again.status -eq $buyer.Result.status -and $again.paymentStatus -eq $buyer.Result.paymentStatus) 'La repetición cambió el resultado de la compra.'
    }
    $again = @(Invoke-PurchaseProbe 'GET' '/api/v1/tickets/me' 200 $null $winner.Token | Where-Object orderId -eq $winner.Order.id)
    Assert-Purchase ($again.Count -eq 1 -and $again[0].ticketCode -eq $ticket.ticketCode) 'La repetición duplicó o reemplazó el ticket.'
    $category = Invoke-PurchaseProbe 'GET' "/api/v1/events/$eventId/ticket-categories/$categoryId" 200
    Assert-Purchase ($category.availableCapacity -eq 0) 'La repetición alteró el aforo.'
    Invoke-PurchaseProbe 'GET' "/api/v1/tickets/$($ticket.ticketCode)" 404 $null $loser.Token | Out-Null
    Invoke-PurchaseProbe 'PATCH' "/api/v1/tickets/$($ticket.ticketCode)/use" 403 $null $winner.Token | Out-Null
    $password = [guid]::NewGuid().ToString('N')
    $email = "staff-$runId@example.com"
    Invoke-PurchaseProbe 'POST' '/api/v1/admin/users/staff' 201 @{ email = $email; password = $password } $adminToken | Out-Null
    $staff = Invoke-PurchaseProbe 'POST' '/api/v1/auth/login' 200 @{ email = $email; password = $password }
    $password = $null
    $tokens.Add($staff.access_token)
    $used = Invoke-PurchaseProbe 'PATCH' "/api/v1/tickets/$($ticket.ticketCode)/use" 200 $null $staff.access_token
    Assert-Purchase ($used.status -eq 'USED' -and $used.usedAt) 'El acceso no registró el uso de la entrada.'
    Invoke-PurchaseProbe 'PATCH' "/api/v1/tickets/$($ticket.ticketCode)/use" 409 $null $staff.access_token | Out-Null
    $completed = $true
    @{ Gateway = $gateway; Checks = $checks; EventId = $eventId; PaidOrderId = $winner.Order.id;
        RefundedOrderId = $loser.Order.id; SimulatedPayment = $true; DuplicateDeliveryVerified = $true; TicketReuseBlocked = $true } |
        ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $local 'aws-purchase-smoke.json')
    Write-Host 'OK: SQS y Lambda reales, emisión, aforo lleno, devolución simulada, repetición sin duplicados y acceso con ticket.'
} finally {
    $password = $null
    # Preserve failed fixtures for diagnosis; do not cancel reservations under an unfinished worker.
    if ($completed -and $eventId -and $adminToken) {
        try { Invoke-PurchaseProbe 'PATCH' "/api/v1/events/$eventId/cancel" 200 $null $adminToken | Out-Null }
        catch { Write-Warning 'Revisar la cancelación del evento de prueba.' }
    }
    foreach ($token in $tokens) {
        try { Invoke-PurchaseProbe 'POST' '/api/v1/auth/logout' 200 $null $token | Out-Null } catch { Write-Warning 'Revisar cierre de sesión de prueba.' }
    }
}
