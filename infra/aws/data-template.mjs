import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export function buildDataTemplate() {
  const ref = name => ({ Ref: name });
  const attr = (name, key) => ({ 'Fn::GetAtt': [name, key] });
  const resources = {};
  const outputs = {};
  const tags = [{ Key: 'Project', Value: 'eventpass' }];
  for (const [name, username, length] of [
    ['AuthDatabaseSecret', 'eventpass_auth', 40], ['EventsDatabaseSecret', 'eventpass_events', 40],
    ['OrdersDatabaseSecret', 'eventpass_orders', 40], ['RedisSecret', null, 40],
    ['JwtSecret', null, 64], ['AdminSecret', null, 48], ['ServiceClientSecret', null, 48]
  ]) {
    resources[name] = { Type: 'AWS::SecretsManager::Secret', DeletionPolicy: 'Retain', UpdateReplacePolicy: 'Retain', Properties: {
      Description: 'EventPass ' + name, Tags: tags,
      GenerateSecretString: { SecretStringTemplate: JSON.stringify(username ? { username } : {}),
        GenerateStringKey: 'password', PasswordLength: length, ExcludePunctuation: true }
    } };
    outputs[name + 'Arn'] = { Value: ref(name) };
  }
  resources.DatabaseParameters = { Type: 'AWS::RDS::DBClusterParameterGroup', Properties: {
    Description: 'EventPass PostgreSQL 17 requires TLS', Family: 'aurora-postgresql17',
    Parameters: { 'rds.force_ssl': '1' }, Tags: tags
  } };
  resources.Database = { Type: 'AWS::RDS::DBCluster', DeletionPolicy: 'Snapshot', UpdateReplacePolicy: 'Snapshot', Properties: {
    DBClusterIdentifier: 'eventpass-aurora', Engine: 'aurora-postgresql', EngineMode: 'provisioned',
    EngineVersion: ref('PostgresVersion'), MasterUsername: 'eventpass_admin', ManageMasterUserPassword: true,
    DBSubnetGroupName: ref('DatabaseSubnetGroup'), VpcSecurityGroupIds: [ref('DataSecurityGroup')],
    DBClusterParameterGroupName: ref('DatabaseParameters'), StorageEncrypted: true,
    BackupRetentionPeriod: 1, Port: 5432, CopyTagsToSnapshot: true, Tags: tags
  } };
  resources.DatabaseWriter = { Type: 'AWS::RDS::DBInstance', Properties: {
    DBInstanceIdentifier: 'eventpass-aurora-writer', DBClusterIdentifier: ref('Database'), Engine: 'aurora-postgresql',
    DBInstanceClass: 'db.t4g.medium', PubliclyAccessible: false, MonitoringInterval: 0,
    EnablePerformanceInsights: false, AutoMinorVersionUpgrade: true, Tags: tags
  } };
  resources.Redis = { Type: 'AWS::ElastiCache::ReplicationGroup', UpdateReplacePolicy: 'Retain', Properties: {
    ReplicationGroupId: 'eventpass-sessions', ReplicationGroupDescription: 'EventPass sessions and token revocation',
    Engine: 'redis', EngineVersion: '7.1', CacheNodeType: 'cache.t3.micro', NumCacheClusters: 1,
    AutomaticFailoverEnabled: false, MultiAZEnabled: false, Port: 6379,
    CacheSubnetGroupName: ref('CacheSubnetGroup'), SecurityGroupIds: [ref('DataSecurityGroup')],
    TransitEncryptionEnabled: true, TransitEncryptionMode: 'required', AtRestEncryptionEnabled: true,
    AuthToken: { 'Fn::Sub': '{{resolve:secretsmanager:${RedisSecret}:SecretString:password}}' },
    SnapshotRetentionLimit: 1, Tags: tags
  } };
  outputs.DatabaseEndpoint = { Value: attr('Database', 'Endpoint.Address') };
  outputs.DatabasePort = { Value: attr('Database', 'Endpoint.Port') };
  outputs.MasterSecretArn = { Value: attr('Database', 'MasterUserSecret.SecretArn') };
  outputs.RedisEndpoint = { Value: attr('Redis', 'PrimaryEndPoint.Address') };
  outputs.RedisPort = { Value: attr('Redis', 'PrimaryEndPoint.Port') };
  return { AWSTemplateFormatVersion: '2010-09-09', Description: 'EventPass private Aurora, Redis and generated secrets',
    Parameters: {
      DatabaseSubnetGroup: { Type: 'String' }, CacheSubnetGroup: { Type: 'String' },
      DataSecurityGroup: { Type: 'AWS::EC2::SecurityGroup::Id' },
      PostgresVersion: { Type: 'String', Default: '17.11', AllowedPattern: '^17\\.\\d+$' }
    }, Resources: resources, Outputs: outputs
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (!process.argv[2]) throw new Error('Uso: node data-template.mjs <salida.json>');
  writeFileSync(process.argv[2], JSON.stringify(buildDataTemplate(), null, 2) + '\n');
}
