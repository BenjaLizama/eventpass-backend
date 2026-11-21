import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export function buildPurchaseConfig(networkOutputs, dataOutputs, queueAttributes) {
  const ids = Object.fromEntries([...networkOutputs, ...dataOutputs].map(x => [x.OutputKey, x.OutputValue]));
  for (const key of ['AppA', 'AppB', 'PurchaseSecurityGroup', 'ServiceClientSecretArn',
    'AuthServiceUrl', 'EventsServiceUrl', 'OrdersServiceUrl', 'TicketsServiceUrl']) {
    if (!ids[key]) throw new Error('Falta output ' + key);
  }
  const account = '119065066402';
  if (!ids.ServiceClientSecretArn.startsWith(`arn:aws:secretsmanager:us-east-1:${account}:secret:`)) {
    throw new Error('El secreto no pertenece a la cuenta y región del laboratorio.');
  }
  const arn = queueAttributes.QueueArn;
  if (arn !== `arn:aws:sqs:us-east-1:${account}:eventpass-purchase-pending` ||
      Number(queueAttributes.VisibilityTimeout) < 120) throw new Error('Cola de compra o visibilidad incorrecta.');
  let redrive;
  try { redrive = JSON.parse(queueAttributes.RedrivePolicy); } catch { throw new Error('Falta la cola de errores.'); }
  if (redrive.deadLetterTargetArn !== `arn:aws:sqs:us-east-1:${account}:eventpass-purchase-errors` ||
      Number(redrive.maxReceiveCount) !== 5) throw new Error('Se requieren cinco intentos y la cola de errores de EventPass.');
  const variables = { MS_ORDERS_CLIENT_ID: 'eventpass-ms-orders', MS_ORDERS_CLIENT_SECRET_ARN: ids.ServiceClientSecretArn };
  for (const name of ['Auth', 'Events', 'Orders', 'Tickets']) {
    const url = new URL(ids[name + 'ServiceUrl']);
    if (url.protocol !== 'http:' || !url.hostname.startsWith('internal-') ||
        !url.hostname.endsWith('.us-east-1.elb.amazonaws.com') || url.username || url.password) {
      throw new Error('La función debe acceder al balanceador privado.');
    }
    variables[name.toUpperCase() + '_SERVICE_URL'] = url.href.replace(/\/$/, '');
  }
  return {
    FunctionName: 'eventpass-process-purchase', Runtime: 'nodejs22.x', Handler: 'index.handler',
    Role: `arn:aws:iam::${account}:role/LabRole`, Timeout: 20, MemorySize: 256,
    Architectures: ['x86_64'],
    VpcConfig: { SubnetIds: [ids.AppA, ids.AppB], SecurityGroupIds: [ids.PurchaseSecurityGroup] },
    Environment: { Variables: variables }
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [network, data, queue, destination] = process.argv.slice(2);
  if (!destination) throw new Error('Uso: purchase-config.mjs <network> <data> <queue-attributes> <output>');
  writeFileSync(destination, JSON.stringify(buildPurchaseConfig(JSON.parse(readFileSync(network)),
    JSON.parse(readFileSync(data)), JSON.parse(readFileSync(queue))), null, 2) + '\n');
}
