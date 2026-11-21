import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { buildNetworkTemplate } from './network-template.mjs';
const routes = JSON.parse(readFileSync(new URL('../gateway/routes.json', import.meta.url)));

test('Gateway publishes exactly the tested route allowlist and preserves the path', () => {
  const { Resources: r } = buildNetworkTemplate(routes);
  const published = Object.values(r).filter(x => x.Type === 'AWS::ApiGatewayV2::Route').map(x => x.Properties.RouteKey);
  assert.deepEqual(published.sort(), Object.values(routes).flat().sort());
  assert.equal(r.Integration.Properties.RequestParameters['overwrite:path'], '$request.path');
  assert.equal(r.Integration.Properties.ConnectionType, 'VPC_LINK');
  assert.equal(r.Balancer.Properties.Scheme, 'internal');
});

test('data subnets have no default route and no subnet assigns public task IPs', () => {
  const { Resources: r } = buildNetworkTemplate(routes);
  assert.equal(Object.values(r).filter(x => x.Type === 'AWS::EC2::Subnet').length, 6);
  assert.ok(Object.values(r).filter(x => x.Type === 'AWS::EC2::Subnet').every(x => x.Properties.MapPublicIpOnLaunch === false));
  const defaults = Object.values(r).filter(x => x.Type === 'AWS::EC2::Route');
  assert.ok(defaults.every(x => x.Properties.RouteTableId.Ref !== 'DataRoutes'));
  assert.deepEqual(r.AppDefaultRoute.Properties.NatGatewayId, { Ref: 'Nat' });
  assert.equal(r.GatewayToBalancer.Properties.SourceSecurityGroupId.Ref, 'VpcLinkSecurityGroup');
  assert.ok(Object.values(r).filter(x => x.Type === 'AWS::EC2::SecurityGroupIngress').every(x => !x.Properties.CidrIp));
});

test('four private IP target groups use the health endpoint and service ports', () => {
  const { Resources: r } = buildNetworkTemplate(routes);
  const targets = Object.values(r).filter(x => x.Type === 'AWS::ElasticLoadBalancingV2::TargetGroup');
  assert.deepEqual(targets.map(x => x.Properties.Port), [8081, 8082, 8083, 8084]);
  assert.ok(targets.every(x => x.Properties.TargetType === 'ip' && x.Properties.HealthCheckPath === '/actuator/health'));
  assert.ok(!Object.values(r).some(x => x.Type.startsWith('AWS::IAM::')));
  assert.ok(!Object.values(r).some(x => x.Type.startsWith('AWS::ServiceDiscovery::')));
  assert.deepEqual(r.AuthInternalListener.Properties.DefaultActions[0].TargetGroupArn, { Ref: 'AuthTargets' });
  assert.equal(r.AuthInternalListener.Properties.Port, 8081);
  assert.equal(r.AppsToInternalBalancer.Properties.SourceSecurityGroupId.Ref, 'AppSecurityGroup');
  assert.equal(r.PurchaseToInternalBalancer.Properties.SourceSecurityGroupId.Ref, 'PurchaseSecurityGroup');
});

test('rejects a wildcard or internal token route', () => {
  for (const route of ['ANY /{proxy+}', 'POST /api/v1/auth/service-token',
    'PATCH /api/v1/events/{id}/reserve', 'PUT /api/v1/events/{id}/reservations/{item}']) {
    const changed = structuredClone(routes);
    changed['ms-auth:8081'].push(route);
    assert.throws(() => buildNetworkTemplate(changed), /no permitida/);
  }
});
