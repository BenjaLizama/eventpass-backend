import test from 'node:test';
import assert from 'node:assert/strict';
import { buildDataTemplate } from './data-template.mjs';

test('Aurora is private, encrypted and requires TLS; master password stays in Secrets Manager', () => {
  const { Resources: r, Outputs: outputs } = buildDataTemplate();
  assert.equal(r.DatabaseWriter.Properties.PubliclyAccessible, false);
  assert.equal(r.Database.Properties.StorageEncrypted, true);
  assert.equal(r.Database.Properties.ManageMasterUserPassword, true);
  assert.ok(!('MasterUserPassword' in r.Database.Properties));
  assert.equal(r.DatabaseParameters.Properties.Parameters['rds.force_ssl'], '1');
  assert.equal(r.Database.DeletionPolicy, 'Snapshot');
  assert.equal(r.DatabaseWriter.Properties.MonitoringInterval, 0);
  assert.equal(outputs.MasterSecretArn.Value['Fn::GetAtt'][1], 'MasterUserSecret.SecretArn');
});

test('each SQL service has separate generated credentials and outputs expose only ARNs', () => {
  const { Resources: r, Outputs: outputs } = buildDataTemplate();
  for (const [name, username] of [['Auth', 'eventpass_auth'], ['Events', 'eventpass_events'], ['Orders', 'eventpass_orders']]) {
    const settings = r[name + 'DatabaseSecret'].Properties.GenerateSecretString;
    assert.equal(JSON.parse(settings.SecretStringTemplate).username, username);
    assert.equal(settings.GenerateStringKey, 'password');
    assert.ok(settings.PasswordLength >= 32);
    assert.equal(r[name + 'DatabaseSecret'].DeletionPolicy, 'Retain');
    assert.deepEqual(outputs[name + 'DatabaseSecretArn'].Value, { Ref: name + 'DatabaseSecret' });
  }
  assert.equal(r.JwtSecret.Properties.GenerateSecretString.PasswordLength, 64);
  assert.ok(!JSON.stringify(outputs).includes('SecretString'));
});

test('Redis requires TLS and authentication and uses private data subnets', () => {
  const p = buildDataTemplate().Resources.Redis.Properties;
  assert.equal(p.TransitEncryptionEnabled, true);
  assert.equal(p.TransitEncryptionMode, 'required');
  assert.equal(p.AtRestEncryptionEnabled, true);
  assert.deepEqual(p.CacheSubnetGroupName, { Ref: 'CacheSubnetGroup' });
  assert.match(p.AuthToken['Fn::Sub'], /resolve:secretsmanager/);
  assert.equal(p.NumCacheClusters, 1);
});
