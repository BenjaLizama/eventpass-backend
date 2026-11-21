import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

export function inspectDeployment(response, expected) {
  if (!Object.keys(expected).length) throw new Error('Faltan las versiones esperadas.');
  const pending = [];
  for (const [name, definition] of Object.entries(expected)) {
    const failure = response.failures?.find(f => f.arn?.endsWith('/' + name) || f.arn === name);
    const service = response.services?.find(s => s.serviceName === name);
    if (failure || (service && service.status !== 'ACTIVE')) {
      return { status: 'FAILED', reason: 'Servicio no disponible: ' + name };
    }
    const primary = service?.deployments?.find(d => d.status === 'PRIMARY');
    const failedVersion = service?.deployments?.find(d => d.taskDefinition === definition && d.rolloutState === 'FAILED');
    if (failedVersion || (service?.taskDefinition === definition && primary?.rolloutState === 'FAILED')) {
      return { status: 'FAILED', reason: 'Falló el despliegue de la versión nueva en ' + name };
    }
    if (!service || service.taskDefinition !== definition ||
        (primary?.taskDefinition ?? service.taskDefinition) !== definition ||
        primary?.rolloutState !== 'COMPLETED' || service.deployments?.length !== 1 ||
        !(service.desiredCount > 0) || service.runningCount !== service.desiredCount || service.pendingCount !== 0) {
      pending.push(name);
    }
  }
  return pending.length ? { status: 'WAITING', pending } : { status: 'READY' };
}

export async function waitForDeployment(read, expected, { attempts = 60, delayMs = 10000, sleep = delay } = {}) {
  for (let attempt = 1; attempt <= attempts; attempt++) {
    const result = inspectDeployment(await read(), expected);
    if (result.status === 'READY') return;
    if (result.status === 'FAILED') throw new Error(result.reason);
    if (attempt < attempts) await sleep(delayMs);
  }
  throw new Error('La versión nueva no quedó activa dentro del tiempo permitido; revisar eventos y rollback en ECS.');
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [expected, observed] = process.argv.slice(2);
  if (!observed) throw new Error('Uso: node service-readiness.mjs <expected.json> <observed.json>');
  console.log(JSON.stringify(inspectDeployment(JSON.parse(readFileSync(observed)), JSON.parse(readFileSync(expected)))));
}
