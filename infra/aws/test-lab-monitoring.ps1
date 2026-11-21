#Requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$local = Join-Path $PSScriptRoot '../../.local'
$awsScript = Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'
function Invoke-AlarmAws([string[]] $Arguments, [string] $InputFile) {
    $value = & $awsScript -AwsArguments ($Arguments + @('--output','json')) -InputFile $InputFile
    if ($value) { $value | ConvertFrom-Json -Depth 40 }
}
function Wait-Alarm([string] $State) {
    for ($attempt = 1; $attempt -le 35; $attempt++) {
        $alarm = (Invoke-AlarmAws -Arguments @('cloudwatch','describe-alarms','--alarm-names','eventpass-purchase-dead-letter')).MetricAlarms[0]
        if ($alarm.StateValue -eq $State) { return $alarm.StateUpdatedTimestamp }
        Write-Host "DLQ: estado $($alarm.StateValue); esperando $State ($attempt/35)."
        Start-Sleep -Seconds 10
    }
    throw "La alarma DLQ no pasó a $State dentro del tiempo permitido."
}
function Remove-OwnProbe {
    for ($attempt = 1; $attempt -le 10; $attempt++) {
        $batch = Invoke-AlarmAws -Arguments @('sqs','receive-message','--queue-url',$queue,'--max-number-of-messages','10','--visibility-timeout','30','--wait-time-seconds','1')
        $deleted = [Collections.Generic.HashSet[string]]::new()
        $found = $false
        try {
            foreach ($message in $batch.Messages) {
                try { $body = $message.Body | ConvertFrom-Json } catch { continue }
                if ($body.type -eq 'eventpass-monitoring-probe' -and $body.probeId -eq $probeId) {
                    Invoke-AlarmAws -Arguments @('sqs','delete-message','--queue-url',$queue,'--receipt-handle',$message.ReceiptHandle) | Out-Null
                    $deleted.Add($message.ReceiptHandle) | Out-Null
                    $found = $true
                }
            }
        } finally {
            foreach ($message in $batch.Messages) {
                if (-not $deleted.Contains($message.ReceiptHandle)) {
                    Invoke-AlarmAws -Arguments @('sqs','change-message-visibility','--queue-url',$queue,'--receipt-handle',$message.ReceiptHandle,'--visibility-timeout','0') | Out-Null
                }
            }
        }
        if ($found) { return $true }
    }
    return $false
}
$identity = Invoke-AlarmAws -Arguments @('sts','get-caller-identity')
if ($identity.Account -ne '119065066402') { throw 'Cuenta incorrecta.' }
$outputs = Get-Content -Raw -LiteralPath (Join-Path $local 'aws-monitoring-outputs.json') | ConvertFrom-Json
$names = (($outputs | Where-Object OutputKey -eq AlarmNames).OutputValue -split ',')
$alarms = (Invoke-AlarmAws -Arguments (@('cloudwatch','describe-alarms','--alarm-names') + $names)).MetricAlarms
if ($alarms.Count -ne 9 -or ($alarms | Where-Object { $_.ActionsEnabled -or $_.AlarmActions.Count -gt 0 })) { throw 'Se requieren nueve alarmas sin acciones externas.' }
$queue = (Invoke-AlarmAws -Arguments @('sqs','get-queue-url','--queue-name','eventpass-purchase-errors')).QueueUrl
$attributes = (Invoke-AlarmAws -Arguments @('sqs','get-queue-attributes','--queue-url',$queue,'--attribute-names','QueueArn','ApproximateNumberOfMessages','ApproximateNumberOfMessagesNotVisible')).Attributes
if ($attributes.QueueArn -ne 'arn:aws:sqs:us-east-1:119065066402:eventpass-purchase-errors') { throw 'DLQ de otra cuenta o región.' }
if ([int]$attributes.ApproximateNumberOfMessages + [int]$attributes.ApproximateNumberOfMessagesNotVisible -ne 0) { throw 'La DLQ contiene compras pendientes. Revisarlas antes de ejecutar la prueba técnica.' }
$probeId = [guid]::NewGuid().ToString('N')
$sendAttempted = $false; $removed = $false
try {
    $baseline = Wait-Alarm 'OK'
    $file = Join-Path $local 'monitoring-probe-request.json'
    @{ QueueUrl = $queue; MessageBody = (@{ type = 'eventpass-monitoring-probe'; probeId = $probeId } | ConvertTo-Json -Compress) } |
        ConvertTo-Json | Set-Content -LiteralPath $file
    $sendAttempted = $true
    $sent = Invoke-AlarmAws -Arguments @('sqs','send-message','--cli-input-json','file:///input/request.json') -InputFile $file
    if (-not $sent.MessageId) { throw 'SQS no confirmó la sonda técnica.' }
    Write-Host 'Sonda técnica en la DLQ; no corresponde a una compra ni se envía al procesador.'
    $alarmed = Wait-Alarm 'ALARM'
    $removed = Remove-OwnProbe
    if (-not $removed) { throw 'No se encontró la sonda para retirarla. Revisar probeId del archivo local.' }
    $recovered = Wait-Alarm 'OK'
    $alarms = (Invoke-AlarmAws -Arguments (@('cloudwatch','describe-alarms','--alarm-names') + $names)).MetricAlarms
    if ($alarms | Where-Object StateValue -ne OK) { throw 'Quedan alarmas fuera de OK. Revisar métricas y servicios.' }
    @{ ProbeId = $probeId; BaselineOK = $baseline; ProbeALARM = $alarmed; RecoveredOK = $recovered;
        AlarmStates = @($alarms | Select-Object AlarmName,StateValue); ProbeRemoved = $removed } |
        ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $local 'aws-monitoring-smoke.json')
    Write-Host 'OK: transición real OK → ALARM → OK por métricas SQS; nueve alarmas sanas y sonda eliminada.'
} finally {
    if ($sendAttempted -and -not $removed) {
        try { if (-not (Remove-OwnProbe)) { Write-Warning 'Revisar si queda la sonda técnica en la DLQ.' } }
        catch { Write-Warning 'No se pudo retirar la sonda técnica; revisar el archivo local y la DLQ.' }
    }
}
