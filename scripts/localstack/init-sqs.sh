#!/bin/bash

echo "Inicializando colas SQS..."

awslocal sqs create-queue --queue-name eventpass-events-published-queue
awslocal sqs create-queue --queue-name eventpass-events-cancelled-queue
awslocal sqs create-queue --queue-name eventpass-orders-completed-queue

echo "Colas SQS creadas exitosamente."