#Requires -Version 7.0
[CmdletBinding(SupportsShouldProcess, ConfirmImpact = 'Medium')]
param()

$ErrorActionPreference = 'Stop'
$endpoint = 'http://127.0.0.1:4567'

function Invoke-Sqs($Operation, $Body) {
    Invoke-RestMethod -Method Post -Uri $endpoint -TimeoutSec 15 `
        -Headers @{ 'X-Amz-Target' = "AmazonSQS.$Operation" } `
        -ContentType 'application/x-amz-json-1.0' -Body ($Body | ConvertTo-Json -Depth 5 -Compress)
}

function Get-QueueArn($Name) {
    $queue = Invoke-Sqs GetQueueUrl @{ QueueName = $Name }
    (Invoke-Sqs GetQueueAttributes @{ QueueUrl = $queue.QueueUrl; AttributeNames = @('QueueArn') }).Attributes.QueueArn
}

if ($PSCmdlet.ShouldProcess('eventpass-purchase-errors', 'Devolver todos los mensajes a la cola de compra local')) {
    $task = Invoke-Sqs StartMessageMoveTask @{
        SourceArn = Get-QueueArn 'eventpass-purchase-errors'
        DestinationArn = Get-QueueArn 'eventpass-purchase-pending'
        MaxNumberOfMessagesPerSecond = 1
    }
    if (-not $task.TaskHandle) { throw 'Floci no confirmó la tarea de recuperación.' }
    $task
}
