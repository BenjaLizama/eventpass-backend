#!/bin/bash
echo "Inicializando colas SQS en LocalStack..."

awslocal sqs create-queue --queue-name eventpass-events-published-queue
awslocal sqs create-queue --queue-name eventpass-events-cancelled-queue

echo "Colas SQS creadas exitosamente."