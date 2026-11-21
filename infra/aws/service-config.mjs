import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const targets = { 'ms-auth': ['Auth', 8081], 'ms-events': ['Events', 8082],
  'ms-orders': ['Orders', 8083], 'ms-tickets': ['Tickets', 8084] };

export function buildServiceConfig(name, taskDefinition, outputs) {
  if (!targets[name]) throw new Error('Microservicio desconocido.');
  if (!/^arn:aws:ecs:us-east-1:119065066402:task-definition\/eventpass-ms-[a-z]+:\d+$/.test(taskDefinition) ||
      !taskDefinition.includes('/eventpass-' + name + ':')) throw new Error('Definición de tarea incorrecta.');
  const ids = Object.fromEntries(outputs.map(x => [x.OutputKey, x.OutputValue]));
  for (const key of ['AppA', 'AppB', 'AppSecurityGroup', targets[name][0] + 'TargetGroupArn']) {
    if (typeof ids[key] !== 'string' || !ids[key]) throw new Error('Falta output ' + key);
  }
  return {
    cluster: 'eventpass', serviceName: 'eventpass-' + name, taskDefinition,
    desiredCount: 1, launchType: 'FARGATE', platformVersion: 'LATEST',
    deploymentController: { type: 'ECS' },
    deploymentConfiguration: { minimumHealthyPercent: 100, maximumPercent: 200,
      deploymentCircuitBreaker: { enable: true, rollback: true } },
    healthCheckGracePeriodSeconds: 180,
    networkConfiguration: { awsvpcConfiguration: {
      subnets: [ids.AppA, ids.AppB], securityGroups: [ids.AppSecurityGroup], assignPublicIp: 'DISABLED'
    } },
    loadBalancers: [{ targetGroupArn: ids[targets[name][0] + 'TargetGroupArn'], containerName: name, containerPort: targets[name][1] }],
    tags: [{ key: 'Project', value: 'eventpass' }]
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [name, taskDefinition, source, destination] = process.argv.slice(2);
  if (!destination) throw new Error('Uso: node service-config.mjs <servicio> <task-arn> <network-outputs> <salida>');
  writeFileSync(destination, JSON.stringify(buildServiceConfig(name, taskDefinition, JSON.parse(readFileSync(source))), null, 2) + '\n');
}
