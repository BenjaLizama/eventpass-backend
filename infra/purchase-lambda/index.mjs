import { processOrder } from './process-order.mjs';

async function readSecret(arn) {
  // AWS supplies SDK v3 and the credentials of the Lambda execution role.
  const { SecretsManagerClient, GetSecretValueCommand } = await import('@aws-sdk/client-secrets-manager');
  return new SecretsManagerClient({}).send(new GetSecretValueCommand({ SecretId: arn }));
}

export async function resolveClientSecret(environment, read = readSecret) {
  if (environment.MS_ORDERS_CLIENT_SECRET_ARN) {
    const result = await read(environment.MS_ORDERS_CLIENT_SECRET_ARN);
    let password;
    try { password = JSON.parse(result.SecretString).password; }
    catch { throw new Error('El secreto del cliente interno no tiene el formato esperado.'); }
    if (typeof password !== 'string' || !password.trim()) throw new Error('El secreto del cliente interno está vacío.');
    return password;
  }
  if (!environment.MS_ORDERS_CLIENT_SECRET) throw new Error('Falta el secreto del cliente interno.');
  return environment.MS_ORDERS_CLIENT_SECRET;
}

async function call(service, path, method, body, token) {
  const response = await fetch(service.replace(/\/+$/, '') + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token && { Authorization: 'Bearer ' + token })
    },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(4000)
  });
  if (!response.ok) throw new Error(method + ' ' + path + ' devolvió HTTP ' + response.status);
  if (response.status !== 204) return (await response.json()).data;
}

export async function handler(event) {
  const auth = process.env.AUTH_SERVICE_URL ?? 'http://ms-auth:8081';
  const events = process.env.EVENTS_SERVICE_URL ?? 'http://ms-events:8082';
  const tickets = process.env.TICKETS_SERVICE_URL ?? 'http://ms-tickets:8084';
  const orders = process.env.ORDERS_SERVICE_URL ?? 'http://ms-orders:8083';
  const credentials = await call(auth, '/api/v1/auth/service-token', 'POST', {
    clientId: process.env.MS_ORDERS_CLIENT_ID,
    clientSecret: await resolveClientSecret(process.env)
  });
  const token = credentials.accessToken;
  if (!token) throw new Error('Auth no devolvió un token de servicio.');
  for (const record of event.Records) {
    await processOrder(JSON.parse(record.body), {
      reserve: item => call(events,
        '/api/v1/events/' + item.eventId + '/reservations/' + item.orderItemId, 'PUT',
        { ticketCategoryId: item.ticketCategoryId, quantity: item.quantity }, token),
      issue: order => call(tickets, '/internal/tickets/issue', 'POST', {
        orderId: order.orderId, userId: order.userId, items: order.items
      }, token),
      complete: id => call(orders, '/internal/purchases/' + id + '/result', 'PUT', 'ISSUED', token),
      refund: id => call(orders, '/internal/purchases/' + id + '/result', 'PUT', 'NO_CAPACITY', token)
    });
  }
}
