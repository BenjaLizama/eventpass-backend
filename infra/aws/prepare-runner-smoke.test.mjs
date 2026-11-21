import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { prepareRunnerSmoke } from './prepare-runner-smoke.mjs';
const config = JSON.parse(readFileSync(new URL('./deploy-config.example.json', import.meta.url)));
const tasks = JSON.parse(readFileSync(new URL('./task-config.example.json', import.meta.url)));
const fn = { FunctionArn: 'worker-arn', PackageType: 'Image', State: 'Active', LastUpdateStatus: 'Successful' };
function fake({ account = config.accountId, type = 'Image', enabled = true, queue = 'eventpass-purchase-pending' } = {}) {
  return (service, operation) => {
    if (service === 'sts') return { Account: account };
    if (operation === 'get-function-configuration') return { ...fn, PackageType: type };
    return { EventSourceMappings: [{ FunctionArn: fn.FunctionArn, State: enabled ? 'Enabled' : 'Disabled', BatchSize: 1, UUID: 'binding',
      EventSourceArn: `arn:aws:sqs:${config.region}:${config.accountId}:${queue}` }] };
  };
}
test('prepares Gateway and real worker references only after confirming the account and SQS binding', () => {
  const files = new Map();
  prepareRunnerSmoke(config, tasks, 'a'.repeat(40), fake(), (name, value) => files.set(name, value));
  assert.equal(files.get('aws-purchase-deployment.json').FunctionArn, fn.FunctionArn);
  assert.equal(files.get('aws-network-outputs.json')[0].OutputValue, config.gatewayUrl);
  assert.deepEqual(files.get('aws-task-config.json').secrets, tasks.secrets);
});
test('wrong account stops before writing runner configuration', () => {
  assert.throws(() => prepareRunnerSmoke(config, tasks, 'a'.repeat(40), fake({ account: 'wrong' }), () => assert.fail('No files allowed')), /Cuenta/);
});
test('a ZIP worker, disabled binding or different queue cannot pass post-deploy preparation', () => {
  for (const options of [{ type: 'Zip' }, { enabled: false }, { queue: 'unrelated' }]) {
    assert.throws(() => prepareRunnerSmoke(config, tasks, 'a'.repeat(40), fake(options), () => assert.fail('No files allowed')), /Lambda/);
  }
});
