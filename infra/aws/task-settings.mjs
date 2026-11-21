import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export function buildTaskSettings(network, data) {
  const n = Object.fromEntries(network.map(x => [x.OutputKey, x.OutputValue]));
  const d = Object.fromEntries(data.map(x => [x.OutputKey, x.OutputValue]));
  const required = (object, name) => {
    if (typeof object[name] !== 'string' || !object[name]) throw new Error('Falta output ' + name);
    return object[name];
  };
  const role = 'arn:aws:iam::119065066402:role/LabRole';
  const selectPassword = name => {
    const arn = required(d, name);
    if (!/^arn:aws:secretsmanager:us-east-1:119065066402:secret:.+/.test(arn)) throw new Error('Referencia de secreto inválida: ' + name);
    return arn + ':password::';
  };
  const environment = {
    REDIS_HOST: required(d, 'RedisEndpoint'), REDIS_PORT: required(d, 'RedisPort'), REDIS_SSL: 'true',
    ADMIN_EMAIL: 'admin@eventpass.cl', MS_ORDERS_CLIENT_ID: 'eventpass-ms-orders',
    AUTH_SERVICE_URL: required(n, 'AuthServiceUrl'), EVENTS_SERVICE_URL: required(n, 'EventsServiceUrl'),
    EVENT_PUBLISHED_QUEUE: 'eventpass-events-published-queue', EVENT_CANCELLED_QUEUE: 'eventpass-events-cancelled-queue',
    ORDER_COMPLETED_QUEUE: 'eventpass-orders-completed-queue', APP_ORDERS_PURCHASE_QUEUE: 'eventpass-purchase-pending',
    TICKETS_DYNAMODB_TABLE: 'eventpass-tickets'
  };
  const secrets = { JWT_SECRET: selectPassword('JwtSecretArn'), ADMIN_PASSWORD: selectPassword('AdminSecretArn'),
    MS_ORDERS_CLIENT_SECRET: selectPassword('ServiceClientSecretArn'), REDIS_PASSWORD: selectPassword('RedisSecretArn') };
  for (const [prefix, user, secret] of [['AUTH', 'eventpass_auth', 'AuthDatabaseSecretArn'],
    ['EVENTS', 'eventpass_events', 'EventsDatabaseSecretArn'], ['ORDERS', 'eventpass_orders', 'OrdersDatabaseSecretArn']]) {
    environment[prefix + '_DB_URL'] = `jdbc:postgresql://${required(d, 'DatabaseEndpoint')}:${required(d, 'DatabasePort')}/${user}?sslmode=verify-full&sslrootcert=/app/rds-ca.pem`;
    environment[prefix + '_DB_USERNAME'] = user;
    secrets[prefix + '_DB_PASSWORD'] = selectPassword(secret);
  }
  const services = ['ms-auth', 'ms-events', 'ms-orders', 'ms-tickets'];
  return { region: 'us-east-1', executionRoleArn: role,
    taskRoles: Object.fromEntries(services.map(s => [s, role])), images: {},
    logGroups: Object.fromEntries(services.map(s => [s, '/ecs/eventpass/' + s])), environment, secrets };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [network, data, destination] = process.argv.slice(2);
  if (!destination) throw new Error('Uso: node task-settings.mjs <network-outputs> <data-outputs> <salida>');
  writeFileSync(destination, JSON.stringify(buildTaskSettings(JSON.parse(readFileSync(network)), JSON.parse(readFileSync(data))), null, 2) + '\n');
}
