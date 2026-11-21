import test from 'node:test';
import assert from 'node:assert/strict';
import { buildServiceConfig } from './service-config.mjs';

const outputs = Object.entries({ AppA: 'subnet-a', AppB: 'subnet-b', AppSecurityGroup: 'sg-app',
  AuthTargetGroupArn: 'auth-target', EventsTargetGroupArn: 'events-target',
  OrdersTargetGroupArn: 'orders-target', TicketsTargetGroupArn: 'tickets-target'
}).map(([OutputKey, OutputValue]) => ({ OutputKey, OutputValue }));

test('each Fargate service has private networking, correct port, health grace and rollback', () => {
  for (const [name, port] of [['ms-auth', 8081], ['ms-events', 8082], ['ms-orders', 8083], ['ms-tickets', 8084]]) {
    const config = buildServiceConfig(name, `arn:aws:ecs:us-east-1:119065066402:task-definition/eventpass-${name}:1`, outputs);
    assert.equal(config.networkConfiguration.awsvpcConfiguration.assignPublicIp, 'DISABLED');
    assert.deepEqual(config.networkConfiguration.awsvpcConfiguration.subnets, ['subnet-a', 'subnet-b']);
    assert.equal(config.loadBalancers[0].containerName, name);
    assert.equal(config.loadBalancers[0].containerPort, port);
    assert.deepEqual(config.deploymentConfiguration.deploymentCircuitBreaker, { enable: true, rollback: true });
    assert.equal(config.healthCheckGracePeriodSeconds, 180);
    assert.equal(config.desiredCount, 1);
  }
});

test('rejects another account, another service task or missing network outputs', () => {
  assert.throws(() => buildServiceConfig('ms-auth', 'arn:aws:ecs:us-east-1:000000000000:task-definition/eventpass-ms-auth:1', outputs), /incorrecta/);
  assert.throws(() => buildServiceConfig('ms-auth', 'arn:aws:ecs:us-east-1:119065066402:task-definition/eventpass-ms-orders:1', outputs), /incorrecta/);
  assert.throws(() => buildServiceConfig('ms-auth', 'arn:aws:ecs:us-east-1:119065066402:task-definition/eventpass-ms-auth:1', []), /Falta output/);
});
