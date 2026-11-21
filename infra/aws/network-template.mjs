import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ref = name => ({ Ref: name });
const attr = (name, field) => ({ 'Fn::GetAtt': [name, field] });
const sub = value => ({ 'Fn::Sub': value });

export function buildNetworkTemplate(routes) {
  const routePolicy = JSON.parse(readFileSync(new URL('../gateway-authorizer/src/main/resources/route-policy.json', import.meta.url)));
  const resources = {};
  const outputs = {};
  const add = (id, Type, Properties, extra = {}) => { resources[id] = { Type, Properties, ...extra }; };
  const output = (id, Value) => { outputs[id] = { Value }; };
  const tag = name => [{ Key: 'Project', Value: 'eventpass' }, { Key: 'Name', Value: name }];
  add('Vpc', 'AWS::EC2::VPC', { CidrBlock: '10.42.0.0/16', EnableDnsSupport: true, EnableDnsHostnames: true, Tags: tag('eventpass') });
  add('InternetGateway', 'AWS::EC2::InternetGateway', { Tags: tag('eventpass') });
  add('GatewayAttachment', 'AWS::EC2::VPCGatewayAttachment', { VpcId: ref('Vpc'), InternetGatewayId: ref('InternetGateway') });
  for (const [tier, prefix, octet] of [['Public', 'public', 0], ['App', 'app-private', 10], ['Data', 'data-private', 20]]) {
    for (const [index, zone] of ['A', 'B'].entries()) {
      add(tier + zone, 'AWS::EC2::Subnet', {
        VpcId: ref('Vpc'), CidrBlock: `10.42.${octet + index}.0/24`,
        AvailabilityZone: ref('Zone' + zone), MapPublicIpOnLaunch: false, Tags: tag(`eventpass-${prefix}-${zone.toLowerCase()}`)
      });
    }
    add(tier + 'Routes', 'AWS::EC2::RouteTable', { VpcId: ref('Vpc'), Tags: tag('eventpass-' + prefix) });
    for (const zone of ['A', 'B']) add(tier + zone + 'RouteAssociation', 'AWS::EC2::SubnetRouteTableAssociation', {
      SubnetId: ref(tier + zone), RouteTableId: ref(tier + 'Routes')
    });
  }
  add('PublicDefaultRoute', 'AWS::EC2::Route', {
    RouteTableId: ref('PublicRoutes'), DestinationCidrBlock: '0.0.0.0/0', GatewayId: ref('InternetGateway')
  }, { DependsOn: 'GatewayAttachment' });
  add('NatAddress', 'AWS::EC2::EIP', { Domain: 'vpc', Tags: tag('eventpass-nat') }, { DependsOn: 'GatewayAttachment' });
  add('Nat', 'AWS::EC2::NatGateway', { SubnetId: ref('PublicA'), AllocationId: attr('NatAddress', 'AllocationId'), Tags: tag('eventpass') });
  add('AppDefaultRoute', 'AWS::EC2::Route', { RouteTableId: ref('AppRoutes'), DestinationCidrBlock: '0.0.0.0/0', NatGatewayId: ref('Nat') });
  for (const service of ['s3', 'dynamodb']) add(service + 'Endpoint', 'AWS::EC2::VPCEndpoint', {
    VpcId: ref('Vpc'), VpcEndpointType: 'Gateway', ServiceName: sub('com.amazonaws.${AWS::Region}.' + service),
    RouteTableIds: [ref('AppRoutes')]
  });

  // Separate rules avoid circular dependencies between the security groups.
  for (const name of ['VpcLink', 'Balancer', 'App', 'Purchase', 'Authorizer', 'Data']) add(name + 'SecurityGroup', 'AWS::EC2::SecurityGroup', {
    GroupDescription: 'EventPass ' + name, VpcId: ref('Vpc'), Tags: tag('eventpass-' + name.toLowerCase()),
    SecurityGroupEgress: name === 'App' || name === 'Purchase'
      ? [{ IpProtocol: '-1', CidrIp: '0.0.0.0/0' }]
      : [{ IpProtocol: 'tcp', FromPort: 1, ToPort: 1, CidrIp: '127.0.0.1/32' }]
  });
  const ingress = (id, destination, source, from, to = from) => add(id, 'AWS::EC2::SecurityGroupIngress', {
    GroupId: ref(destination + 'SecurityGroup'), SourceSecurityGroupId: ref(source + 'SecurityGroup'),
    IpProtocol: 'tcp', FromPort: from, ToPort: to
  });
  const egress = (id, source, destination, from, to = from) => add(id, 'AWS::EC2::SecurityGroupEgress', {
    GroupId: ref(source + 'SecurityGroup'), DestinationSecurityGroupId: ref(destination + 'SecurityGroup'),
    IpProtocol: 'tcp', FromPort: from, ToPort: to
  });
  ingress('GatewayToBalancer', 'Balancer', 'VpcLink', 80);
  egress('GatewayEgress', 'VpcLink', 'Balancer', 80);
  ingress('BalancerToApps', 'App', 'Balancer', 8081, 8084);
  egress('BalancerEgress', 'Balancer', 'App', 8081, 8084);
  ingress('AppsToInternalBalancer', 'Balancer', 'App', 8081, 8084);
  ingress('PurchaseToInternalBalancer', 'Balancer', 'Purchase', 8081, 8084);
  ingress('AppsToPostgres', 'Data', 'App', 5432);
  ingress('AppsToRedis', 'Data', 'App', 6379);
  add('DatabaseSubnets', 'AWS::RDS::DBSubnetGroup', {
    DBSubnetGroupDescription: 'EventPass private data', SubnetIds: [ref('DataA'), ref('DataB')], Tags: tag('eventpass')
  });
  add('CacheSubnets', 'AWS::ElastiCache::SubnetGroup', {
    Description: 'EventPass private cache', SubnetIds: [ref('DataA'), ref('DataB')]
  });
  add('Balancer', 'AWS::ElasticLoadBalancingV2::LoadBalancer', {
    Scheme: 'internal', Type: 'application', SecurityGroups: [ref('BalancerSecurityGroup')],
    Subnets: [ref('AppA'), ref('AppB')], Tags: tag('eventpass')
  });
  add('Listener', 'AWS::ElasticLoadBalancingV2::Listener', {
    LoadBalancerArn: ref('Balancer'), Port: 80, Protocol: 'HTTP', DefaultActions: [{ Type: 'fixed-response',
      FixedResponseConfig: { StatusCode: '404', ContentType: 'application/json', MessageBody: '{"message":"Route not found"}' } }]
  });
  const services = [
    ['Auth', 'ms-auth:8081', 8081, ['/api/v1/auth/*', '/api/v1/admin/*']],
    ['Events', 'ms-events:8082', 8082, ['/api/v1/events', '/api/v1/events/*', '/api/v1/venues', '/api/v1/venues/*']],
    ['Orders', 'ms-orders:8083', 8083, ['/api/v1/orders', '/api/v1/orders/*']],
    ['Tickets', 'ms-tickets:8084', 8084, ['/api/v1/tickets', '/api/v1/tickets/*']]
  ];
  for (const [index, [name, origin, port, paths]] of services.entries()) {
    if (!Array.isArray(routes[origin])) throw new Error('Faltan rutas para ' + origin);
    add(name + 'Targets', 'AWS::ElasticLoadBalancingV2::TargetGroup', {
      VpcId: ref('Vpc'), TargetType: 'ip', Protocol: 'HTTP', Port: port,
      HealthCheckPath: '/actuator/health', HealthCheckIntervalSeconds: 30,
      HealthyThresholdCount: 2, UnhealthyThresholdCount: 3, Matcher: { HttpCode: '200' },
      TargetGroupAttributes: [{ Key: 'deregistration_delay.timeout_seconds', Value: '30' }]
    });
    add(name + 'Forward', 'AWS::ElasticLoadBalancingV2::ListenerRule', {
      ListenerArn: ref('Listener'), Priority: index + 1,
      Conditions: [{ Field: 'path-pattern', PathPatternConfig: { Values: paths } }],
      Actions: [{ Type: 'forward', TargetGroupArn: ref(name + 'Targets') }]
    });
    // Cloud Map namespace creation is denied in Learner Lab. Stable, private
    // ALB listeners preserve service-specific URLs for ECS callers and Lambda.
    add(name + 'InternalListener', 'AWS::ElasticLoadBalancingV2::Listener', {
      LoadBalancerArn: ref('Balancer'), Port: port, Protocol: 'HTTP',
      DefaultActions: [{ Type: 'forward', TargetGroupArn: ref(name + 'Targets') }]
    });
    output(name + 'TargetGroupArn', ref(name + 'Targets'));
    output(name + 'ServiceUrl', { 'Fn::Join': ['', ['http://', attr('Balancer', 'DNSName'), ':' + port]] });
  }
  add('VpcLink', 'AWS::ApiGatewayV2::VpcLink', { Name: 'eventpass', SubnetIds: [ref('AppA'), ref('AppB')], SecurityGroupIds: [ref('VpcLinkSecurityGroup')] });
  add('Api', 'AWS::ApiGatewayV2::Api', { Name: 'eventpass', ProtocolType: 'HTTP', DisableExecuteApiEndpoint: false });
  add('Integration', 'AWS::ApiGatewayV2::Integration', {
    ApiId: ref('Api'), IntegrationType: 'HTTP_PROXY', IntegrationMethod: 'ANY',
    IntegrationUri: ref('Listener'), ConnectionType: 'VPC_LINK', ConnectionId: ref('VpcLink'),
    PayloadFormatVersion: '1.0', RequestParameters: { 'overwrite:path': '$request.path' }
  });
  add('GatewayAuthorizer', 'AWS::ApiGatewayV2::Authorizer', {
    ApiId: ref('Api'), Name: 'eventpass-session-and-role', AuthorizerType: 'REQUEST',
    AuthorizerPayloadFormatVersion: '2.0', EnableSimpleResponses: true,
    AuthorizerResultTtlInSeconds: 0, IdentitySource: ['$request.header.Authorization'],
    AuthorizerUri: sub('arn:aws:apigateway:${AWS::Region}:lambda:path/2015-03-31/functions/${GatewayAuthorizerArn}/invocations')
  }, { Condition: 'HasGatewayAuthorizer' });
  add('GatewayAuthorizerPermission', 'AWS::Lambda::Permission', {
    FunctionName: ref('GatewayAuthorizerArn'), Action: 'lambda:InvokeFunction', Principal: 'apigateway.amazonaws.com',
    SourceAccount: ref('AWS::AccountId'),
    SourceArn: sub('arn:aws:execute-api:${AWS::Region}:${AWS::AccountId}:${Api}/authorizers/${GatewayAuthorizer}')
  }, { Condition: 'HasGatewayAuthorizer' });
  ingress('AuthorizerToAuth', 'Balancer', 'Authorizer', 8081);
  egress('AuthorizerToAuthEgress', 'Authorizer', 'Balancer', 8081);
  const routeKeys = Object.values(routes).flat();
  if (new Set(routeKeys).size !== routeKeys.length) throw new Error('Rutas de Gateway duplicadas.');
  for (const [index, route] of routeKeys.entries()) {
    if (!/^(GET|POST|PUT|PATCH|DELETE) \/api\/v1\//.test(route) || /\*|\{proxy\+\}|service-token|\/internal\/|\/reservations(?:\/|$)|\/(?:reserve|release)(?:\/|$)/.test(route)) {
      throw new Error('Ruta pública no permitida: ' + route);
    }
    if (!Array.isArray(routePolicy[route]) || !routePolicy[route].length) throw new Error('Falta política explícita de ruta: ' + route);
    const authorization = routePolicy[route].includes('PUBLIC') ? { AuthorizationType: 'NONE' } : {
      AuthorizationType: { 'Fn::If': ['HasGatewayAuthorizer', 'CUSTOM', 'NONE'] },
      AuthorizerId: { 'Fn::If': ['HasGatewayAuthorizer', ref('GatewayAuthorizer'), ref('AWS::NoValue')] }
    };
    add('Route' + index, 'AWS::ApiGatewayV2::Route', { ApiId: ref('Api'), RouteKey: route, ...authorization,
      Target: { 'Fn::Join': ['/', ['integrations', ref('Integration')]] } });
  }
  add('Stage', 'AWS::ApiGatewayV2::Stage', { ApiId: ref('Api'), StageName: '$default', AutoDeploy: true,
    DefaultRouteSettings: { ThrottlingBurstLimit: 100, ThrottlingRateLimit: 50 } });
  for (const name of ['Vpc', 'AppA', 'AppB', 'DataA', 'DataB', 'DatabaseSubnets', 'CacheSubnets',
    'AppSecurityGroup', 'PurchaseSecurityGroup', 'AuthorizerSecurityGroup', 'DataSecurityGroup', 'Listener', 'VpcLink', 'Api']) output(name, ref(name));
  output('GatewayUrl', attr('Api', 'ApiEndpoint'));
  return {
    AWSTemplateFormatVersion: '2010-09-09', Description: 'EventPass Learner Lab private network and HTTP API',
    Conditions: { HasGatewayAuthorizer: { 'Fn::Not': [{ 'Fn::Equals': [ref('GatewayAuthorizerArn'), ''] }] } },
    Parameters: { GatewayAuthorizerArn: { Type: 'String', Default: '', AllowedPattern: '^$|^arn:aws:lambda:us-east-1:119065066402:function:eventpass-gateway-authorizer$' },
      ZoneA: { Type: 'AWS::EC2::AvailabilityZone::Name', Default: 'us-east-1a' },
      ZoneB: { Type: 'AWS::EC2::AvailabilityZone::Name', Default: 'us-east-1b' } },
    Rules: { DifferentZones: { Assertions: [{ Assert: { 'Fn::Not': [{ 'Fn::Equals': [ref('ZoneA'), ref('ZoneB')] }] }, AssertDescription: 'Two availability zones are required.' }] } },
    Resources: resources, Outputs: outputs
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const destination = process.argv[2];
  if (!destination) throw new Error('Uso: node network-template.mjs <salida.json>');
  const routes = JSON.parse(readFileSync(new URL('../gateway/routes.json', import.meta.url)));
  writeFileSync(destination, JSON.stringify(buildNetworkTemplate(routes), null, 2) + '\n');
}
