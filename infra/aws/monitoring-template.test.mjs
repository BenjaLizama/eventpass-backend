import test from 'node:test';
import assert from 'node:assert/strict';
import { buildMonitoringTemplate } from './monitoring-template.mjs';
const prefix = 'arn:aws:elasticloadbalancing:us-east-1:119065066402:';
const outputs = Object.entries({ Listener: prefix + 'listener/app/eventpass/abc/listener',
  ...Object.fromEntries(['Auth','Events','Orders','Tickets'].map(n => [n + 'TargetGroupArn', prefix + 'targetgroup/' + n + '/abc']))
}).map(([OutputKey, OutputValue]) => ({ OutputKey, OutputValue }));
const fn = 'arn:aws:lambda:us-east-1:119065066402:function:eventpass-process-purchase-image';

test('monitors DLQ visible and in-flight messages without external or destructive actions', () => {
  const template = buildMonitoringTemplate(outputs, fn);
  const alarms = Object.values(template.Resources).filter(r => r.Type === 'AWS::CloudWatch::Alarm');
  assert.equal(alarms.length, 9);
  for (const { Properties: p } of alarms) {
    assert.deepEqual(p.AlarmActions, []);
    assert.equal(p.ActionsEnabled, false);
  }
  const dlq = template.Resources.PurchaseDeadLetter.Properties;
  assert.equal(dlq.Metrics.at(-1).Expression, 'FILL(visible,0)+FILL(inflight,0)');
  assert.equal(dlq.EvaluationPeriods, 3);
  assert.equal(dlq.DatapointsToAlarm, 2);
});

test('availability alarms use exact ALB metric dimensions and detect absent targets', () => {
  const template = buildMonitoringTemplate(outputs, fn);
  const alarm = template.Resources.AuthAvailability.Properties;
  assert.deepEqual(alarm.Dimensions, [{ Name: 'TargetGroup', Value: 'targetgroup/Auth/abc' }, { Name: 'LoadBalancer', Value: 'app/eventpass/abc' }]);
  assert.equal(alarm.TreatMissingData, 'breaching');
  assert.equal(alarm.ComparisonOperator, 'LessThanThreshold');
  assert.equal(template.Resources.PurchaseLambdaDuration.Properties.Threshold, 15000);
  assert.equal(JSON.parse(template.Resources.Dashboard.Properties.DashboardBody).widgets[0].properties.alarms.length, 9);
});

test('rejects another account, an old ZIP worker or foreign target groups before publication', () => {
  assert.throws(() => buildMonitoringTemplate(outputs, fn.replace('119065066402','000000000000')), /Función/);
  assert.throws(() => buildMonitoringTemplate(outputs, fn.replace('-image','')), /Función/);
  assert.throws(() => buildMonitoringTemplate(outputs.map(x => x.OutputKey === 'AuthTargetGroupArn' ? { ...x, OutputValue: 'foreign' } : x), fn), /Target group/);
});
