import test from 'node:test';
import assert from 'node:assert/strict';
import { buildPurchaseConfig } from './purchase-config.mjs';
const outputs = Object.entries({ AppA: 'subnet-a', AppB: 'subnet-b', PurchaseSecurityGroup: 'sg-purchase',
  ServiceClientSecretArn: 'arn:aws:secretsmanager:us-east-1:119065066402:secret:client',
  ...Object.fromEntries(['Auth','Events','Orders','Tickets'].map((name, i) =>
    [name + 'ServiceUrl', `http://internal-eventpass.us-east-1.elb.amazonaws.com:${8081 + i}`]))
}).map(([OutputKey, OutputValue]) => ({ OutputKey, OutputValue }));
const attributes = { QueueArn: 'arn:aws:sqs:us-east-1:119065066402:eventpass-purchase-pending',
  VisibilityTimeout: '120', RedrivePolicy: JSON.stringify({
    deadLetterTargetArn: 'arn:aws:sqs:us-east-1:119065066402:eventpass-purchase-errors', maxReceiveCount: '5' }) };

test('Lambda uses private app subnets, LabRole, a secret reference and compatible SQS visibility', () => {
  const config = buildPurchaseConfig(outputs, [], attributes);
  assert.equal(config.Timeout * 6, Number(attributes.VisibilityTimeout));
  assert.equal(config.PackageType, 'Image');
  assert.equal(config.Runtime, undefined);
  assert.equal(config.Handler, undefined);
  assert.deepEqual(config.VpcConfig.SubnetIds, ['subnet-a', 'subnet-b']);
  assert.equal(config.VpcConfig.SecurityGroupIds[0], 'sg-purchase');
  assert.equal(config.Environment.Variables.MS_ORDERS_CLIENT_SECRET, undefined);
  assert.match(config.Environment.Variables.MS_ORDERS_CLIENT_SECRET_ARN, /:secret:client$/);
});

test('rejects public service URLs, foreign accounts and missing retry protection', () => {
  assert.throws(() => buildPurchaseConfig(outputs, [], { ...attributes, VisibilityTimeout: '20' }), /visibilidad/);
  assert.throws(() => buildPurchaseConfig(outputs, [], { ...attributes, RedrivePolicy: '{}' }), /cinco intentos/);
  assert.throws(() => buildPurchaseConfig(outputs, [], { ...attributes, QueueArn: 'foreign' }), /Cola/);
  assert.throws(() => buildPurchaseConfig(outputs.map(x => x.OutputKey === 'AuthServiceUrl'
    ? { ...x, OutputValue: 'https://public.example.com' } : x), [], attributes), /privado/);
});
