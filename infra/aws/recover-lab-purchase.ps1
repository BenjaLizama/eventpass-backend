#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][guid] $OrderId,
    [ValidateRange(1,100)][int] $MaxBatches = 10,
    [string] $AwsScript = (Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'),
    [string] $WorkingDirectory = (Join-Path $PSScriptRoot '../../.local')
)
$ErrorActionPreference = 'Stop'
if ($OrderId -eq [guid]::Empty) { throw 'OrderId no puede estar vacío.' }
function Invoke-RecoveryAws([string[]] $Arguments, [string] $InputFile) {
    $result = & $AwsScript -AwsArguments ($Arguments + @('--output','json')) -InputFile $InputFile
    if ($result) { $result | ConvertFrom-Json -Depth 40 }
}
$identity = Invoke-RecoveryAws -Arguments @('sts','get-caller-identity')
if ($identity.Account -ne '119065066402') { throw 'Cuenta incorrecta. No se recuperarán mensajes.' }
$source = (Invoke-RecoveryAws -Arguments @('sqs','get-queue-url','--queue-name','eventpass-purchase-errors')).QueueUrl
$destination = (Invoke-RecoveryAws -Arguments @('sqs','get-queue-url','--queue-name','eventpass-purchase-pending')).QueueUrl
foreach ($pair in @(@{ Url = $source; Name = 'eventpass-purchase-errors' }, @{ Url = $destination; Name = 'eventpass-purchase-pending' })) {
    $attributes = (Invoke-RecoveryAws -Arguments @('sqs','get-queue-attributes','--queue-url',$pair.Url,'--attribute-names','QueueArn')).Attributes
    if ($attributes.QueueArn -ne "arn:aws:sqs:us-east-1:119065066402:$($pair.Name)") { throw 'Cola fuera de la cuenta o región autorizada.' }
}
New-Item -ItemType Directory -Path $WorkingDirectory -Force | Out-Null
for ($attempt = 1; $attempt -le $MaxBatches; $attempt++) {
    $batch = Invoke-RecoveryAws -Arguments @('sqs','receive-message','--queue-url',$source,'--max-number-of-messages','10',
        '--visibility-timeout','30','--wait-time-seconds','1','--message-attribute-names','All')
    $deleted = [Collections.Generic.HashSet[string]]::new()
    try {
        foreach ($message in $batch.Messages) {
            try { $body = $message.Body | ConvertFrom-Json -Depth 20 } catch { continue }
            if ($body.orderId -ne $OrderId.ToString()) { continue }
            if (@($body.items).Count -ne 1 -or -not $body.userId) { throw 'La orden seleccionada contiene un mensaje inválido. No se reenvió.' }
            $request = @{ QueueUrl = $destination; MessageBody = $message.Body }
            if ($message.MessageAttributes) { $request.MessageAttributes = $message.MessageAttributes }
            $file = Join-Path $WorkingDirectory 'purchase-recovery-request.json'
            $request | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $file
            $sent = Invoke-RecoveryAws -Arguments @('sqs','send-message','--cli-input-json','file:///input/request.json') -InputFile $file
            if (-not $sent.MessageId) { throw 'SQS no confirmó el reenvío. El mensaje permanece en la cola de errores.' }
            # Delete only after SQS confirms receipt. A crash between these calls may duplicate delivery;
            # the reservation, issuance and final result must remain idempotent.
            Invoke-RecoveryAws -Arguments @('sqs','delete-message','--queue-url',$source,'--receipt-handle',$message.ReceiptHandle) | Out-Null
            $deleted.Add($message.ReceiptHandle) | Out-Null
            Write-Host "Orden $OrderId reenviada; comprobar su resultado y el ticket."
            return [pscustomobject]@{ OrderId = $OrderId.ToString(); MessageId = $sent.MessageId; Forwarded = $true }
        }
    } finally {
        foreach ($message in $batch.Messages) {
            if (-not $deleted.Contains($message.ReceiptHandle)) {
                try { Invoke-RecoveryAws -Arguments @('sqs','change-message-visibility','--queue-url',$source,
                    '--receipt-handle',$message.ReceiptHandle,'--visibility-timeout','0') | Out-Null }
                catch { Write-Warning 'No se pudo liberar un mensaje; su visibilidad se recuperará tras 30 segundos.' }
            }
        }
    }
}
throw 'La orden no apareció en los lotes examinados. No se borraron otros mensajes; revisar la DLQ o repetir la búsqueda.'
