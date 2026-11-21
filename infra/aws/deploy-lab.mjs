import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { waitForDeployment } from './service-readiness.mjs';

const services = ['ms-auth', 'ms-events', 'ms-orders', 'ms-tickets'];
const required = (value, label, pattern) => {
  if (typeof value !== 'string' || !value.trim() || (pattern && !pattern.test(value))) {
    throw new Error('Configuración inválida: ' + label);
  }
  return value;
};

export function validateConfig(config, tasks, revision) {
  required(revision, 'commit', /^[a-f0-9]{40}$/);
  required(config.accountId, 'accountId', /^\d{12}$/);
  required(config.region, 'region', /^us-(east|west)-[12]$/);
  if (config.region !== tasks.region) throw new Error('Las regiones de despliegue y tareas difieren.');
  required(config.cluster, 'cluster', /^[\w-]+$/);
  required(config.purchaseFunction, 'purchaseFunction', /^[\w-]+$/);
  const url = new URL(required(config.gatewayUrl, 'gatewayUrl'));
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash ||
      !url.hostname.endsWith(`.execute-api.${config.region}.amazonaws.com`)) {
    throw new Error('gatewayUrl debe ser la URL HTTPS de API Gateway en la región configurada.');
  }
  for (const service of services) {
    required(config.services?.[service], 'services.' + service, /^[\w-]+$/);
    required(config.repositories?.[service], 'repositories.' + service, /^[a-z0-9]+(?:[._/-][a-z0-9]+)*$/);
  }
  if (new Set(Object.values(config.services)).size !== services.length ||
      new Set(Object.values(config.repositories)).size !== services.length) {
    throw new Error('Cada microservicio requiere su propio servicio ECS y repositorio ECR.');
  }
  const arns = [tasks.executionRoleArn, ...services.map(s => tasks.taskRoles?.[s]),
    ...Object.values(tasks.secrets ?? {})];
  for (const arn of arns) {
    if (typeof arn !== 'string' || arn.split(':')[4] !== config.accountId) {
      throw new Error('Los roles y secretos deben pertenecer a la cuenta confirmada.');
    }
  }
}

export async function deploy(config, taskConfig, revision, run, checkOnly = false, smoke = smokeGateway, waitOptions = {}) {
  validateConfig(config, taskConfig, revision);
  const workspace = mkdtempSync(join(tmpdir(), 'eventpass-deploy-'));
  const aws = (...args) => run('aws', [...args, '--region', config.region, '--output', 'json', '--no-cli-pager']);
  try {
    // Validate generated tasks before contacting AWS or publishing anything.
    const configFile = join(workspace, 'config.json');
    writeFileSync(configFile, JSON.stringify(taskConfig));
    run(process.execPath, ['infra/aws/render-task-definitions.mjs', configFile, workspace]);
    const identity = aws('sts', 'get-caller-identity');
    if (identity.Account !== config.accountId) throw new Error('La identidad AWS pertenece a otra cuenta.');
    const repositories = aws('ecr', 'describe-repositories', '--repository-names',
      ...services.map(s => config.repositories[s]));
    if (repositories.repositories?.length !== services.length) throw new Error('Faltan repositorios ECR.');
    const current = aws('ecs', 'describe-services', '--cluster', config.cluster, '--services',
      ...services.map(s => config.services[s]));
    if (current.failures?.length || current.services?.length !== services.length) throw new Error('Faltan servicios ECS.');
    for (const service of current.services) {
      if (service.status !== 'ACTIVE' || service.desiredCount < 1 ||
          service.deploymentController?.type !== 'ECS' ||
          !service.deploymentConfiguration?.deploymentCircuitBreaker?.enable ||
          !service.deploymentConfiguration?.deploymentCircuitBreaker?.rollback) {
        throw new Error('ECS necesita servicios activos con rollback automático: ' + service.serviceName);
      }
    }
    const fn = aws('lambda', 'get-function-configuration', '--function-name', config.purchaseFunction);
    if (fn.State !== 'Active' || fn.Runtime !== 'nodejs22.x' || fn.Handler !== 'index.handler' ||
        fn.LastUpdateStatus === 'InProgress') throw new Error('Lambda no está preparada para actualizar su código.');
    const mappings = aws('lambda', 'list-event-source-mappings', '--function-name', config.purchaseFunction);
    if (!mappings.EventSourceMappings?.some(m => m.State === 'Enabled' && m.BatchSize === 1 &&
      m.EventSourceArn?.startsWith(`arn:aws:sqs:${config.region}:${config.accountId}:`) &&
      m.FunctionArn === fn.FunctionArn)) {
      throw new Error('Falta el vínculo SQS → Lambda habilitado, sin alias y con BatchSize 1.');
    }
    await smoke(config.gatewayUrl);
    if (checkOnly) return { checked: true, accountId: identity.Account };

    const assertLatest = () => {
      const head = run('git', ['ls-remote', 'origin', 'refs/heads/develop']).trim().split(/\s/)[0];
      if (head !== revision) throw new Error('develop avanzó; esta ejecución antigua no debe desplegarse.');
    };
    assertLatest();

    const registry = `${config.accountId}.dkr.ecr.${config.region}.amazonaws.com`;
    const imageTag = revision + '-' + randomUUID();
    const password = run('aws', ['ecr', 'get-login-password', '--region', config.region], { raw: true });
    run('docker', ['login', '--username', 'AWS', '--password-stdin', registry], { input: password });
    const images = {};
    // Publish all images before replacing any running task.
    for (const service of services) {
      const tag = `${registry}/${config.repositories[service]}:${imageTag}`;
      run('docker', ['build', '--platform', 'linux/amd64', '-t', tag, service]);
      run('docker', ['push', tag]);
      const result = aws('ecr', 'describe-images', '--repository-name', config.repositories[service],
        '--image-ids', `imageTag=${imageTag}`);
      const digest = result.imageDetails?.[0]?.imageDigest;
      required(digest, service + ' digest', /^sha256:[a-f0-9]{64}$/);
      images[service] = `${registry}/${config.repositories[service]}@${digest}`;
    }
    writeFileSync(configFile, JSON.stringify({ ...taskConfig, images }));
    run(process.execPath, ['infra/aws/render-task-definitions.mjs', configFile, workspace]);
    assertLatest();
    const definitions = {};
    for (const service of services) {
      const result = aws('ecs', 'register-task-definition', '--cli-input-json', `file://${join(workspace, service + '.json')}`);
      definitions[service] = result.taskDefinition.taskDefinitionArn;
    }
    const zip = join(workspace, 'purchase.zip');
    run('zip', ['-j', zip, 'infra/purchase-lambda/index.mjs', 'infra/purchase-lambda/process-order.mjs']);
    // Keep function environment, VPC, permissions and SQS mapping unchanged.
    aws('lambda', 'update-function-code', '--function-name', config.purchaseFunction,
      '--revision-id', fn.RevisionId, '--zip-file', `fileb://${zip}`);
    aws('lambda', 'wait', 'function-updated-v2', '--function-name', config.purchaseFunction);
    for (const service of services) {
      aws('ecs', 'update-service', '--cluster', config.cluster, '--service', config.services[service],
        '--task-definition', definitions[service]);
    }
    await waitForDeployment(() => aws('ecs', 'describe-services', '--cluster', config.cluster,
      '--services', ...services.map(s => config.services[s])),
    Object.fromEntries(services.map(s => [config.services[s], definitions[s]])), waitOptions);
    await smoke(config.gatewayUrl);
    return { revision, images, definitions };
  } finally {
    rmSync(workspace, { recursive: true, force: true });
  }
}

async function smokeGateway(base) {
  for (const [path, expected] of [['/api/v1/events?page=0&size=1', 200], ['/api/v1/orders/my-orders', 401]]) {
    const response = await fetch(base.replace(/\/$/, '') + path, { signal: AbortSignal.timeout(15000), redirect: 'error' });
    if (response.status !== expected) throw new Error(`Gateway ${path}: HTTP ${response.status}, se esperaba ${expected}`);
  }
  const response = await fetch(base.replace(/\/$/, '') + '/api/v1/auth/service-token', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}', signal: AbortSignal.timeout(15000), redirect: 'error'
  });
  if (response.status !== 404) throw new Error('La ruta interna de tokens no quedó oculta en el Gateway.');
}

function command(executable, args, options = {}) {
  // Capture AWS responses, which can contain Lambda environment secrets. Never log them.
  try {
    const output = execFileSync(executable, args, {
      encoding: 'utf8', input: options.input, maxBuffer: 16 * 1024 * 1024,
      stdio: executable === 'docker' && args[0] !== 'login' ? 'inherit' : ['pipe', 'pipe', 'pipe']
    });
    return executable !== 'aws' || options.raw ? output : (output?.trim() ? JSON.parse(output) : {});
  } catch {
    throw new Error(`Falló ${executable} ${args[0] ?? ''} ${executable === 'aws' ? args[1] : ''}; revisar permisos y recursos de la sala.`);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  // JSON comes through environment variables, never interpolated into shell commands.
  const result = await deploy(JSON.parse(process.env.AWS_DEPLOY_CONFIG), JSON.parse(process.env.AWS_TASK_CONFIG),
    process.env.DEPLOY_SHA, command, process.argv.includes('--check'));
  console.log(JSON.stringify(result, null, 2));
}
