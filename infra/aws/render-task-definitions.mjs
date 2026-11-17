import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const [source, destination] = process.argv.slice(2);
if (!source || !destination) {
  throw new Error('Uso: node render-task-definitions.mjs <config.json> <directorio>');
}

const config = JSON.parse(readFileSync(source, 'utf8'));
const services = {
  'ms-auth': {
    port: 8081, settings: ['AUTH_DB_URL', 'AUTH_DB_USERNAME', 'ADMIN_EMAIL'],
    secrets: ['AUTH_DB_PASSWORD', 'ADMIN_PASSWORD', 'MS_ORDERS_CLIENT_SECRET']
  },
  'ms-events': {
    port: 8082, settings: ['EVENTS_DB_URL', 'EVENTS_DB_USERNAME', 'EVENT_PUBLISHED_QUEUE', 'EVENT_CANCELLED_QUEUE'],
    secrets: ['EVENTS_DB_PASSWORD']
  },
  'ms-orders': {
    port: 8083, settings: ['ORDERS_DB_URL', 'ORDERS_DB_USERNAME', 'AUTH_SERVICE_URL', 'EVENTS_SERVICE_URL',
      'ORDER_COMPLETED_QUEUE', 'APP_ORDERS_PURCHASE_QUEUE'],
    secrets: ['ORDERS_DB_PASSWORD', 'MS_ORDERS_CLIENT_SECRET']
  },
  'ms-tickets': {
    port: 8084, settings: ['TICKETS_DYNAMODB_TABLE', 'ORDER_COMPLETED_QUEUE'], secrets: []
  }
};

function required(object, key) {
  const value = object?.[key];
  if (typeof value !== 'string' || !value.trim()) throw new Error('Falta ' + key);
  return value;
}

const region = required(config, 'region');
const executionRoleArn = required(config, 'executionRoleArn');
const tasks = Object.entries(services).map(([name, service]) => {
  const image = required(config.images, name);
  if (!/^\d{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com\/.+@sha256:[a-f0-9]{64}$/.test(image)) {
    throw new Error(name + ' requiere una imagen ECR fijada por digest.');
  }
  const environment = {
    AWS_REGION: region,
    SPRING_PROFILES_ACTIVE: name === 'ms-tickets' ? 'prod,dynamodb' : 'prod',
    REDIS_HOST: required(config.environment, 'REDIS_HOST'),
    REDIS_PORT: required(config.environment, 'REDIS_PORT'),
    REDIS_SSL: required(config.environment, 'REDIS_SSL')
  };
  for (const key of service.settings) environment[key] = required(config.environment, key);
  if (name === 'ms-orders') environment.APP_ORDERS_ASYNC_PURCHASE = 'true';
  if (name === 'ms-auth' || name === 'ms-orders') {
    environment.MS_ORDERS_CLIENT_ID = required(config.environment, 'MS_ORDERS_CLIENT_ID');
  }

  const secrets = ['JWT_SECRET', ...service.secrets];
  if (config.secrets?.REDIS_PASSWORD) secrets.push('REDIS_PASSWORD');
  return [name, {
    family: 'eventpass-' + name,
    requiresCompatibilities: ['FARGATE'],
    networkMode: 'awsvpc',
    runtimePlatform: { operatingSystemFamily: 'LINUX', cpuArchitecture: 'X86_64' },
    cpu: '512',
    memory: '1024',
    executionRoleArn,
    taskRoleArn: required(config.taskRoles, name),
    containerDefinitions: [{
      name, image, essential: true,
      portMappings: [{ containerPort: service.port, protocol: 'tcp' }],
      environment: Object.entries(environment).map(([name, value]) => ({ name, value })),
      secrets: secrets.map(name => {
        const valueFrom = required(config.secrets, name);
        if (!/^arn:[^:]+:(secretsmanager|ssm):/.test(valueFrom)) {
          throw new Error(name + ' requiere una referencia ARN a un secreto.');
        }
        return { name, valueFrom };
      }),
      logConfiguration: {
        logDriver: 'awslogs',
        options: {
          'awslogs-group': required(config.logGroups, name),
          'awslogs-region': region,
          'awslogs-stream-prefix': 'eventpass'
        }
      }
    }]
  }];
});

mkdirSync(destination, { recursive: true });
for (const [name, task] of tasks) {
  writeFileSync(join(destination, name + '.json'), JSON.stringify(task, null, 2) + '\n');
}
