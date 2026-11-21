import test from 'node:test';
import assert from 'node:assert/strict';
import { buildTaskSettings } from './task-settings.mjs';

const outputs = values => Object.entries(values).map(([OutputKey, OutputValue]) => ({ OutputKey, OutputValue }));
test('task settings select JSON secret keys, verify RDS certificates and keep master credentials out of applications', () => {
  const n = outputs({ AuthServiceUrl: 'http://private:8081', EventsServiceUrl: 'http://private:8082' });
  const d = outputs({ DatabaseEndpoint: 'private.rds.amazonaws.com', DatabasePort: '5432', RedisEndpoint: 'private.cache.amazonaws.com', RedisPort: '6379',
    ...Object.fromEntries(['Jwt','Admin','ServiceClient','Redis','AuthDatabase','EventsDatabase','OrdersDatabase','Master'].map(name => [name + 'SecretArn',
      `arn:aws:secretsmanager:us-east-1:119065066402:secret:${name}-abcdef`])) });
  const result = buildTaskSettings(n, d);
  assert.equal(result.environment.REDIS_SSL, 'true');
  assert.match(result.environment.AUTH_DB_URL, /sslmode=verify-full&sslrootcert=\/app\/rds-ca.pem$/);
  assert.ok(Object.values(result.secrets).every(s => s.endsWith(':password::')));
  assert.ok(!JSON.stringify(result.secrets).includes(':Master-'));
  assert.deepEqual(result.images, {}); // ECR digests must be supplied before deployment.
  assert.equal(result.environment.ORDERS_DB_USERNAME, 'eventpass_orders');
});
