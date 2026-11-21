import test from 'node:test';
import assert from 'node:assert/strict';
import { inspectDeployment, waitForDeployment } from './service-readiness.mjs';
const expected = { auth: 'new:auth' };
const response = (state = 'COMPLETED', definition = 'new:auth') => ({ services: [{
  serviceName: 'auth', status: 'ACTIVE', taskDefinition: definition, desiredCount: 1, runningCount: 1, pendingCount: 0,
  deployments: [{ status: 'PRIMARY', rolloutState: state, taskDefinition: definition }]
}] });

test('running tasks do not prove rollout completion; waits for the requested version', async () => {
  assert.equal(inspectDeployment(response('IN_PROGRESS'), expected).status, 'WAITING');
  assert.equal(inspectDeployment(response('COMPLETED', 'old:auth'), expected).status, 'WAITING');
  let calls = 0;
  await waitForDeployment(async () => ++calls < 3 ? response('IN_PROGRESS') : response(), expected,
    { attempts: 3, sleep: async () => {} });
  assert.equal(calls, 3);
});

test('failed deployments and rollback cannot be reported as success', async () => {
  await assert.rejects(waitForDeployment(async () => response('FAILED'), expected), /Falló/);
  await assert.rejects(waitForDeployment(async () => response('COMPLETED', 'old:auth'), expected,
    { attempts: 2, sleep: async () => {} }), /versión nueva/);
});

test('rejects missing services and waits for old tasks to drain', () => {
  assert.equal(inspectDeployment({ failures: [{ arn: 'auth', reason: 'MISSING' }] }, expected).status, 'FAILED');
  const draining = response();
  draining.services[0].deployments.push({ status: 'ACTIVE', taskDefinition: 'old:auth' });
  assert.equal(inspectDeployment(draining, expected).status, 'WAITING');
  assert.equal(inspectDeployment(response(), expected).status, 'READY');
});
