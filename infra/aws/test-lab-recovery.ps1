#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
$temporaryBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$temporary = Join-Path $temporaryBase ('eventpass-recovery-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $temporary -Force | Out-Null
$fake = Join-Path $temporary 'fake-aws.ps1'
@'
param([string[]] $AwsArguments, [string] $InputFile)
Invoke-FakeRecoveryAws $AwsArguments $InputFile | ConvertTo-Json -Depth 30 -Compress
'@ | Set-Content -LiteralPath $fake
$selected = '123e4567-e89b-42d3-a456-426614174000'
$message = @{ Body = (@{ orderId = $selected; userId = 'customer'; items = @(@{ quantity = 1 }) } | ConvertTo-Json -Compress);
    ReceiptHandle = 'selected-receipt'; MessageAttributes = @{ Trace = @{ DataType = 'String'; StringValue = 'preserved' } } }
$other = @{ Body = '{"orderId":"another-order"}'; ReceiptHandle = 'other-receipt' }
function Assert-Recovery($Condition, $Message) { if (-not $Condition) { throw $Message } }
function global:Invoke-FakeRecoveryAws($Arguments, $InputFile) {
    $operation = $Arguments[0..1] -join ' '
    $global:recoveryCalls.Add(@{ Operation = $operation; Arguments = $Arguments })
    switch ($operation) {
        'sts get-caller-identity' { return @{ Account = $global:recoveryAccount } }
        'sqs get-queue-url' { return @{ QueueUrl = 'https://sqs.us-east-1.amazonaws.com/119065066402/' + $Arguments[$Arguments.IndexOf('--queue-name') + 1] } }
        'sqs get-queue-attributes' {
            if ($global:recoveryWrongQueue) { return @{ Attributes = @{ QueueArn = 'arn:aws:sqs:us-west-2:000000000000:foreign' } } }
            return @{ Attributes = @{ QueueArn = 'arn:aws:sqs:us-east-1:119065066402:' + ($Arguments[$Arguments.IndexOf('--queue-url') + 1] -split '/')[-1] } }
        }
        'sqs receive-message' { return @{ Messages = $global:recoveryMessages } }
        'sqs send-message' {
            if ($global:recoveryFailSend) { throw 'Simulated connection failure' }
            $global:recoverySent = Get-Content -Raw -LiteralPath $InputFile | ConvertFrom-Json
            if ($global:recoveryUnconfirmed) { return @{} }
            return @{ MessageId = 'confirmed' }
        }
        'sqs delete-message' {
            if ($global:recoveryFailDelete) { throw 'Simulated delete failure after forwarding' }
            return @{}
        }
        'sqs change-message-visibility' { return @{} }
        default { throw "Operación inesperada: $operation" }
    }
}
try {
    foreach ($case in @('success','send-failure','send-unconfirmed','delete-failure','missing','wrong-account','wrong-queue')) {
        $global:recoveryCalls = [Collections.Generic.List[object]]::new()
        $global:recoveryAccount = if ($case -eq 'wrong-account') { '000000000000' } else { '119065066402' }
        $global:recoveryMessages = if ($case -eq 'missing') { @($other) } else { @($other, $message) }
        $global:recoveryFailSend = $case -eq 'send-failure'
        $global:recoveryUnconfirmed = $case -eq 'send-unconfirmed'
        $global:recoveryFailDelete = $case -eq 'delete-failure'
        $global:recoveryWrongQueue = $case -eq 'wrong-queue'
        $failed = $false
        try {
            $result = & (Join-Path $PSScriptRoot 'recover-lab-purchase.ps1') -OrderId $selected -MaxBatches 1 -AwsScript $fake -WorkingDirectory $temporary
        } catch { $failed = $true }
        $deleted = @($global:recoveryCalls | Where-Object Operation -eq 'sqs delete-message')
        $released = @($global:recoveryCalls | Where-Object Operation -eq 'sqs change-message-visibility')
        if ($case -eq 'success') {
            Assert-Recovery (-not $failed -and $result.Forwarded) 'La recuperación confirmada debe finalizar.'
            Assert-Recovery ($global:recoverySent.MessageBody -eq $message.Body) 'Debe conservar el cuerpo original.'
            Assert-Recovery ($global:recoverySent.MessageAttributes.Trace.StringValue -eq 'preserved') 'Debe conservar atributos.'
            Assert-Recovery ($deleted.Count -eq 1 -and $deleted[0].Arguments -contains 'selected-receipt') 'Solo puede borrar la orden seleccionada.'
            Assert-Recovery ($released.Count -eq 1 -and $released[0].Arguments -contains 'other-receipt') 'Debe liberar el mensaje ajeno.'
            $operations = @($global:recoveryCalls.Operation)
            Assert-Recovery ($operations.IndexOf('sqs send-message') -lt $operations.IndexOf('sqs delete-message')) 'Debe confirmar envío antes de borrar.'
        } elseif ($case -eq 'delete-failure') {
            Assert-Recovery ($failed -and $deleted.Count -eq 1 -and $released.Count -eq 2) 'Si falla el borrado tras el envío, el mensaje debe quedar recuperable; puede repetirse la entrega.'
        } else {
            Assert-Recovery ($failed -and $deleted.Count -eq 0) 'Un fallo no puede borrar mensajes.'
            if ($case -in @('send-failure','send-unconfirmed')) { Assert-Recovery ($released.Count -eq 2) 'Un envío no confirmado debe liberar ambos mensajes.' }
            if ($case -eq 'wrong-account') { Assert-Recovery ($global:recoveryCalls.Count -eq 1) 'Cuenta incorrecta debe detenerse antes de SQS.' }
            if ($case -eq 'wrong-queue') { Assert-Recovery (-not ($global:recoveryCalls | Where-Object Operation -eq 'sqs receive-message')) 'Una cola de otra región/cuenta debe rechazarse antes de recibir mensajes.' }
        }
    }
    Write-Host 'OK: selección de orden, preservación del mensaje, envío confirmado antes de borrado, fallos de envío/borrado y cuenta/cola incorrectas.'
} finally {
    Remove-Item Function:\Invoke-FakeRecoveryAws -ErrorAction SilentlyContinue
    $resolvedTemporary = [IO.Path]::GetFullPath($temporary)
    if (-not $resolvedTemporary.StartsWith($temporaryBase.TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar)) { throw 'Ruta de limpieza fuera del directorio temporal.' }
    Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force
}
