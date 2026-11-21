#!/bin/sh
set -eu
# Floci simula SQS. La creación repetida de una cola conserva la existente.
for queue in eventpass-events-published-queue eventpass-events-cancelled-queue eventpass-orders-completed-queue; do
  attempt=0
  until aws --endpoint-url http://floci:4566 sqs create-queue --queue-name "$queue" >/dev/null; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 60 ]; then exit 1; fi
    sleep 2
  done
done
echo 'Colas SQS locales preparadas.'
