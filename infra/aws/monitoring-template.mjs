import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export function buildMonitoringTemplate(outputs, functionArn) {
  const account = '119065066402', region = 'us-east-1';
  const name = 'eventpass-process-purchase-image';
  if (functionArn !== `arn:aws:lambda:${region}:${account}:function:${name}`) throw new Error('Función de otra cuenta, región o despliegue.');
  const ids = Object.fromEntries(outputs.map(x => [x.OutputKey, x.OutputValue]));
  const prefix = `arn:aws:elasticloadbalancing:${region}:${account}:`;
  const listener = ids.Listener;
  if (!listener?.startsWith(prefix + 'listener/app/')) throw new Error('Listener fuera de la red autorizada.');
  const balancer = listener.slice((prefix + 'listener/').length).split('/').slice(0, 3).join('/');
  const resources = {}, alarmNames = [];
  const alarm = (id, suffix, properties) => {
    const alarmName = 'eventpass-' + suffix;
    alarmNames.push(alarmName);
    resources[id] = { Type: 'AWS::CloudWatch::Alarm', Properties: {
      AlarmName: alarmName, ActionsEnabled: false, AlarmActions: [], OKActions: [], InsufficientDataActions: [],
      EvaluationPeriods: 2, DatapointsToAlarm: 2, TreatMissingData: 'notBreaching',
      ...properties
    } };
  };
  const queueMetric = (metric, queue) => ({ Namespace: 'AWS/SQS', MetricName: metric,
    Dimensions: [{ Name: 'QueueName', Value: queue }] });
  alarm('PurchaseDeadLetter', 'purchase-dead-letter', {
    AlarmDescription: 'DLQ con mensajes en dos de tres muestras de un minuto. Tolera una muestra retrasada; revisar causa y recuperar por orderId.',
    EvaluationPeriods: 3,
    ComparisonOperator: 'GreaterThanOrEqualToThreshold', Threshold: 1,
    Metrics: [
      { Id: 'visible', MetricStat: { Metric: queueMetric('ApproximateNumberOfMessagesVisible', 'eventpass-purchase-errors'), Period: 60, Stat: 'Maximum' }, ReturnData: false },
      { Id: 'inflight', MetricStat: { Metric: queueMetric('ApproximateNumberOfMessagesNotVisible', 'eventpass-purchase-errors'), Period: 60, Stat: 'Maximum' }, ReturnData: false },
      { Id: 'depth', Expression: 'FILL(visible,0)+FILL(inflight,0)', Label: 'Mensajes en DLQ', ReturnData: true }
    ]
  });
  alarm('PurchaseQueueAge', 'purchase-queue-age', {
    AlarmDescription: 'Mensaje pendiente en SQS con antigüedad superior a cinco minutos. No cuenta órdenes fuera de SQS.',
    ...queueMetric('ApproximateAgeOfOldestMessage', 'eventpass-purchase-pending'),
    Period: 60, Statistic: 'Maximum', ComparisonOperator: 'GreaterThanThreshold', Threshold: 300
  });
  for (const [id, metric, threshold] of [['Errors','Errors',1],['Duration','Duration',15000],['Throttles','Throttles',1]]) {
    alarm('PurchaseLambda' + id, 'lambda-' + id.toLowerCase(), {
      AlarmDescription: id === 'Duration' ? 'La ejecución alcanzó quince segundos; el timeout de la función es veinte.' : 'Revisar ' + metric + ' del procesador de compras.',
      Namespace: 'AWS/Lambda', MetricName: metric, Dimensions: [{ Name: 'FunctionName', Value: name }],
      Period: 60, Statistic: id === 'Duration' ? 'Maximum' : 'Sum', EvaluationPeriods: 1, DatapointsToAlarm: 1,
      ComparisonOperator: 'GreaterThanOrEqualToThreshold', Threshold: threshold
    });
  }
  const healthMetrics = [];
  for (const [key, suffix] of [['Auth','auth'],['Events','events'],['Orders','orders'],['Tickets','tickets']]) {
    const arn = ids[key + 'TargetGroupArn'];
    if (!arn?.startsWith(prefix + 'targetgroup/')) throw new Error('Target group inválido: ' + key);
    const target = arn.slice(prefix.length);
    const dimensions = [{ Name: 'TargetGroup', Value: target }, { Name: 'LoadBalancer', Value: balancer }];
    alarm(key + 'Availability', suffix + '-unavailable', {
      AlarmDescription: 'No hay al menos un destino sano para ' + suffix + ' durante dos minutos. Revisar ECS y target group.',
      Namespace: 'AWS/ApplicationELB', MetricName: 'HealthyHostCount', Dimensions: dimensions,
      Period: 60, Statistic: 'Minimum', Threshold: 1, ComparisonOperator: 'LessThanThreshold', TreatMissingData: 'breaching'
    });
    healthMetrics.push(['AWS/ApplicationELB','HealthyHostCount','TargetGroup',target,'LoadBalancer',balancer,{ label: suffix, stat: 'Minimum' }]);
  }
  const metricWidget = (title, x, y, metrics, extra = {}) => ({ type: 'metric', x, y, width: 12, height: 6,
    properties: { title, region, period: 60, view: 'timeSeries', metrics, ...extra } });
  const widgets = [
    { type: 'alarm', x: 0, y: 0, width: 24, height: 4, properties: { title: 'EventPass — estado de alarmas',
      alarms: alarmNames.map(n => `arn:aws:cloudwatch:${region}:${account}:alarm:${n}`) } },
    metricWidget('Destinos sanos de los cuatro microservicios', 0, 4, healthMetrics),
    metricWidget('Colas: mensajes pendientes y en la DLQ', 12, 4,
      ['eventpass-purchase-pending','eventpass-purchase-errors'].flatMap(q => ['ApproximateNumberOfMessagesVisible','ApproximateNumberOfMessagesNotVisible']
        .map(m => ['AWS/SQS', m, 'QueueName', q, { stat: 'Maximum', label: q.replace('eventpass-', '') + ' ' + m.replace('ApproximateNumberOfMessages', '') }]))),
    metricWidget('Lambda: errores y límites de concurrencia', 0, 10,
      ['Errors','Throttles'].map(m => ['AWS/Lambda',m,'FunctionName',name,{ stat: 'Sum' }])),
    metricWidget('Lambda: duración máxima (ms)', 12, 10,
      [['AWS/Lambda','Duration','FunctionName',name,{ stat: 'Maximum' }]], { annotations: { horizontal: [{ label: 'Advertencia 15 s', value: 15000 }] } }),
    metricWidget('Eventos: CPU real y objetivo de autoescalado', 0, 16,
      [['AWS/ECS','CPUUtilization','ClusterName','eventpass','ServiceName','eventpass-ms-events',{ stat: 'Average' }]],
      { yAxis: { left: { min: 0, max: 100 } }, annotations: { horizontal: [{ label: 'Objetivo CPU 60%', value: 60 }] } }),
    metricWidget('Eventos: destinos sanos del ALB', 12, 16,
      [['AWS/ApplicationELB','HealthyHostCount','TargetGroup',ids.EventsTargetGroupArn.slice(prefix.length),'LoadBalancer',balancer,{ stat: 'Minimum' }]],
      { yAxis: { left: { min: 0 } }, annotations: { horizontal: [{ label: 'Mínimo 1', value: 1 }, { label: 'Máximo 2', value: 2 }] } }),
    { type: 'text', x: 0, y: 22, width: 24, height: 4, properties: { markdown:
      '## Cómo actuar\nDLQ: revisar logs del procesador y recuperar únicamente la orden afectada después de corregir la causa.\n\nSin destinos sanos: revisar tareas y despliegues de ECS. Las nueve alarmas operativas no tienen acciones externas. El escalado de Eventos usa por separado las alarmas administradas de target tracking: correlacionar CPU, destinos sanos e historial de Application Auto Scaling. No confundir dos tareas durante un despliegue con una subida por carga. La antigüedad de SQS no reemplaza una auditoría de órdenes en SQL.' } }
  ];
  resources.Dashboard = { Type: 'AWS::CloudWatch::Dashboard', Properties: { DashboardName: 'eventpass-lab', DashboardBody: JSON.stringify({ widgets }) } };
  return { AWSTemplateFormatVersion: '2010-09-09', Description: 'EventPass lab monitoring without external actions',
    Resources: resources, Outputs: { DashboardName: { Value: 'eventpass-lab' }, AlarmNames: { Value: alarmNames.join(',') } } };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [network, deployment, destination] = process.argv.slice(2);
  if (!destination) throw new Error('Uso: monitoring-template.mjs <network> <purchase-deployment> <output>');
  writeFileSync(destination, JSON.stringify(buildMonitoringTemplate(JSON.parse(readFileSync(network)), JSON.parse(readFileSync(deployment)).FunctionArn), null, 2) + '\n');
}
