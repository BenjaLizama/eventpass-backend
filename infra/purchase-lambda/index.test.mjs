import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { test } from 'node:test';
import { handler } from './index.mjs';

test('usa las direcciones privadas configuradas para la compra', async () => {
  const calls = [];
  const server = createServer((request, response) => {
    calls.push([request.method, request.url, request.headers.authorization]);
    if (request.url.startsWith('/auth/')) {
      response.end(JSON.stringify({ data: { accessToken: 'test-token' } }));
    } else if (request.url.startsWith('/events/')) {
      response.end(JSON.stringify({ data: { status: 'RESERVED' } }));
    } else {
      response.writeHead(204).end();
    }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = 'http://127.0.0.1:' + server.address().port;
  const previous = {};
  for (const name of ['AUTH', 'EVENTS', 'TICKETS', 'ORDERS']) {
    const key = name + '_SERVICE_URL';
    previous[key] = process.env[key];
    process.env[key] = base + '/' + name.toLowerCase() + '/';
  }
  try {
    await handler({ Records: [{ body: JSON.stringify({
      orderId: 'order', userId: 'user',
      items: [{ orderItemId: 'item', eventId: 'event', ticketCategoryId: 'category', quantity: 1 }]
    }) }] });
    assert.deepEqual(calls, [
      ['POST', '/auth/api/v1/auth/service-token', undefined],
      ['PUT', '/events/api/v1/events/event/reservations/item', 'Bearer test-token'],
      ['POST', '/tickets/internal/tickets/issue', 'Bearer test-token'],
      ['PUT', '/orders/internal/purchases/order/result', 'Bearer test-token']
    ]);
  } finally {
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) delete process.env[key];
      else process.env[key] = value;
    }
    await new Promise(resolve => server.close(resolve));
  }
});
