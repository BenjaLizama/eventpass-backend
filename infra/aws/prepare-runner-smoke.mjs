import { mkdirSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { validateConfig } from './deploy-lab.mjs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export function prepareRunnerSmoke(config, tasks, revision, aws, save) {
  validateConfig(config, tasks, revision);
  if (aws('sts','get-caller-identity').Account !== config.accountId) throw new Error('Cuenta incorrecta.');
  const fn = aws('lambda','get-function-configuration','--function-name',config.purchaseFunction);
  const mappings = aws('lambda','list-event-source-mappings','--function-name',config.purchaseFunction).EventSourceMappings ?? [];
  const queueArn = `arn:aws:sqs:${config.region}:${config.accountId}:eventpass-purchase-pending`;
  const mapping = mappings.find(m => m.State === 'Enabled' && m.BatchSize === 1 && m.FunctionArn === fn.FunctionArn && m.EventSourceArn === queueArn);
  if (fn.PackageType !== 'Image' || fn.State !== 'Active' || fn.LastUpdateStatus !== 'Successful' || !mapping) throw new Error('Lambda o vínculo no disponibles para la prueba.');
  save('aws-network-outputs.json', [{ OutputKey: 'GatewayUrl', OutputValue: config.gatewayUrl }]);
  save('aws-task-config.json', tasks);
  save('aws-purchase-deployment.json', { FunctionArn: fn.FunctionArn, State: mapping.State, MappingId: mapping.UUID });
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const config = JSON.parse(process.env.AWS_DEPLOY_CONFIG), tasks = JSON.parse(process.env.AWS_TASK_CONFIG);
  const aws = (...args) => JSON.parse(execFileSync('aws', [...args, '--region', config.region, '--output', 'json', '--no-cli-pager'], { encoding: 'utf8' }));
  const save = (name, value) => { mkdirSync('.local', { recursive: true }); writeFileSync('.local/' + name, JSON.stringify(value, null, 2)); };
  prepareRunnerSmoke(config, tasks, process.env.DEPLOY_SHA, aws, save);
  console.log('Configuración de prueba preparada con referencias de secretos, sin valores de credenciales.');
}
