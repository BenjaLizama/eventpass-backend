import { processOrder } from './process-order.mjs';

async function call(service, path, method, body, token) {
  const response = await fetch('http://' + service + path, {
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
  const credentials = await call('ms-auth:8081', '/api/v1/auth/service-token', 'POST', {
    clientId: process.env.MS_ORDERS_CLIENT_ID,
    clientSecret: process.env.MS_ORDERS_CLIENT_SECRET
  });
  const token = credentials.accessToken;
  if (!token) throw new Error('Auth no devolvió un token de servicio.');
  for (const record of event.Records) {
    await processOrder(JSON.parse(record.body), {
      reserve: item => call('ms-events:8082',
        '/api/v1/events/' + item.eventId + '/reservations/' + item.orderItemId, 'PUT',
        { ticketCategoryId: item.ticketCategoryId, quantity: item.quantity }, token),
      issue: order => call('ms-tickets:8084', '/internal/tickets/issue', 'POST', {
        orderId: order.orderId, userId: order.userId, items: order.items
      }, token),
      complete: id => call('ms-orders:8083', '/internal/purchases/' + id + '/result', 'PUT', 'ISSUED', token),
      refund: id => call('ms-orders:8083', '/internal/purchases/' + id + '/result', 'PUT', 'NO_CAPACITY', token)
    });
  }
}
