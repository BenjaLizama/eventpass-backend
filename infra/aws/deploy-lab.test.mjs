import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { deploy, validateConfig } from './deploy-lab.mjs';

const config = JSON.parse(readFileSync(new URL('./deploy-config.example.json', import.meta.url)));
const tasks = JSON.parse(readFileSync(new URL('./task-config.example.json', import.meta.url)));
const sha = 'b'.repeat(40);
const names = Object.keys(config.services);

function fake({ account = config.accountId, rollback = false, stableOldVersion = false, mapping = true, head = sha, packageType = 'Image' } = {}) {
  const calls = [];
  let deployed = false;
  const run = (bin, args, options) => {
    calls.push({ bin, args, options });
    if (bin === process.execPath) {
      execFileSync(bin, args); // Exercise the real task renderer, including digest substitution.
      return;
    }
    if (bin === 'git') return head + '\trefs/heads/develop\n';
    if (bin !== 'aws') return '';
    const operation = args.slice(0, 2).join(' ');
    if (operation === 'sts get-caller-identity') return { Account: account };
    if (operation === 'ecr describe-repositories') return { repositories: Array.from({ length: names.length + 1 }, () => ({})) };
    if (operation === 'ecs describe-services') return {
      failures: [], services: names.map(name => ({
        serviceName: config.services[name], status: 'ACTIVE', desiredCount: 1, runningCount: 1, pendingCount: 0,
        deploymentController: { type: 'ECS' },
        deploymentConfiguration: { deploymentCircuitBreaker: { enable: true, rollback: !rollback } },
        taskDefinition: deployed && !stableOldVersion ? 'new:' + name : 'old:' + name,
        deployments: [{ status: 'PRIMARY', rolloutState: 'COMPLETED' }]
      }))
    };
    if (operation === 'lambda get-function-configuration') return {
      State: 'Active', PackageType: packageType, RevisionId: 'old-revision', FunctionArn: 'function-arn'
    };
    if (operation === 'lambda list-event-source-mappings') return { EventSourceMappings: mapping ? [{
      State: 'Enabled', BatchSize: 1, FunctionArn: 'function-arn',
      EventSourceArn: `arn:aws:sqs:${config.region}:${config.accountId}:purchase`
    }] : [] };
    if (operation === 'ecr get-login-password') return 'temporary-password';
    if (operation === 'ecr describe-images') return { imageDetails: [{ imageDigest: 'sha256:' + 'c'.repeat(64) }] };
    if (operation === 'ecs register-task-definition') {
      const path = args[args.indexOf('--cli-input-json') + 1].slice(7);
      const definition = JSON.parse(readFileSync(path));
      assert.match(definition.containerDefinitions[0].image, /@sha256:c{64}$/);
      return { taskDefinition: { taskDefinitionArn: 'new:' + definition.containerDefinitions[0].name } };
    }
    if (operation === 'ecs update-service') deployed = true;
    return {};
  };
  return { calls, run };
}

test('rejects wrong account configuration and mixed regions before contacting AWS', () => {
  assert.throws(() => validateConfig({ ...config, accountId: '000000000000' }, tasks, sha), /cuenta/);
  assert.throws(() => validateConfig({ ...config, region: 'us-west-2' }, tasks, sha), /regiones/);
  assert.throws(() => validateConfig(config, tasks, 'develop'), /commit/);
  assert.throws(() => validateConfig({ ...config, purchaseRepository: config.repositories['ms-auth'] }, tasks, sha), /repositorio/);
});

test('preflight checks resources and Gateway without publishing or updating anything', async () => {
  const mock = fake();
  let checks = 0;
  assert.deepEqual(await deploy(config, tasks, sha, mock.run, true, async () => checks++), {
    checked: true, accountId: config.accountId
  });
  assert.equal(checks, 1);
  assert.equal(mock.calls.some(c => c.bin === 'docker' || /update|register/.test(c.args[1])), false);
});

for (const [label, options, error] of [
  ['wrong credentials', { account: '000000000000' }, /otra cuenta/],
  ['missing rollback', { rollback: true }, /rollback/],
  ['missing SQS mapping', { mapping: false }, /SQS/],
  ['legacy ZIP Lambda', { packageType: 'Zip' }, /Lambda/]
]) {
  test(label + ' stops before publication', async () => {
    const mock = fake(options);
    await assert.rejects(deploy(config, tasks, sha, mock.run, false, async () => {}), error);
    assert.equal(mock.calls.some(c => c.bin === 'docker' || /update|register/.test(c.args[1])), false);
  });
}

test('publishes five pinned Docker images before updating ECS and image Lambda, then verifies Gateway', async () => {
  const mock = fake();
  let checks = 0;
  const result = await deploy(config, tasks, sha, mock.run, false, async () => checks++);
  assert.equal(result.revision, sha);
  assert.equal(Object.keys(result.images).length, 4);
  assert.equal(checks, 2);
  const pushes = mock.calls.map((c, i) => c.bin === 'docker' && c.args[0] === 'push' ? i : -1).filter(i => i >= 0);
  const updates = mock.calls.map((c, i) => c.args.slice(0, 2).join(' ') === 'ecs update-service' ? i : -1).filter(i => i >= 0);
  assert.equal(pushes.length, 5);
  assert.equal(updates.length, 4);
  assert.ok(Math.max(...pushes) < Math.min(...updates));
  assert.ok(mock.calls.some(c => c.args.includes('--revision-id') && c.args.includes('old-revision')));
  const lambdaUpdate = mock.calls.find(c => c.args.slice(0, 2).join(' ') === 'lambda update-function-code');
  assert.ok(lambdaUpdate.args.includes('--image-uri'));
  assert.ok(!lambdaUpdate.args.includes('--zip-file'));
  assert.match(result.purchaseImage, /@sha256:c{64}$/);
  const login = mock.calls.find(c => c.bin === 'docker' && c.args[0] === 'login');
  assert.equal(login.options.input, 'temporary-password');
  assert.equal(login.args.includes('temporary-password'), false);
});

test('an ECS rollback cannot be reported as successful deployment', async () => {
  const mock = fake({ stableOldVersion: true });
  await assert.rejects(deploy(config, tasks, sha, mock.run, false, async () => {},
    { attempts: 2, sleep: async () => {} }), /versión nueva/);
});

test('Gateway failure prevents all publications', async () => {
  const mock = fake();
  await assert.rejects(deploy(config, tasks, sha, mock.run, false, async () => { throw new Error('Gateway caído'); }), /Gateway caído/);
  assert.equal(mock.calls.some(c => c.bin === 'docker'), false);
});

test('an older successful test run cannot deploy over the latest develop commit', async () => {
  const mock = fake({ head: 'd'.repeat(40) });
  await assert.rejects(deploy(config, tasks, sha, mock.run, false, async () => {}), /ejecución antigua/);
  assert.equal(mock.calls.some(c => c.bin === 'docker'), false);
});
