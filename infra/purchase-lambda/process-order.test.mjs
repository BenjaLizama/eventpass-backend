import assert from 'node:assert/strict';
import { test } from 'node:test';
import { processOrder } from './process-order.mjs';

const order = { orderId: 'order', userId: 'customer', items: [{ orderItemId: 'item' }] };

test('registra la emisión después de reservar y emitir', async () => {
  const steps = [];
  await processOrder(order, {
    reserve: async item => {
      assert.equal(item.orderItemId, 'item');
      return { status: 'RESERVED' };
    },
    issue: async purchase => steps.push(purchase.orderId),
    complete: async id => steps.push('complete:' + id),
    refund: async () => assert.fail('Se reservó aforo')
  });
  assert.deepEqual(steps, ['order', 'complete:order']);
});

test('solicita devolución solo cuando Eventos rechaza la reserva', async () => {
  const refunded = [];
  await processOrder(order, {
    reserve: async () => ({ status: 'REJECTED' }),
    issue: async () => assert.fail('Se rechazó la reserva'),
    complete: async () => assert.fail('Se rechazó la reserva'),
    refund: async id => refunded.push(id)
  });
  assert.deepEqual(refunded, ['order']);
});

test('propaga el fallo de emisión para permitir un reintento', async () => {
  await assert.rejects(processOrder(order, {
    reserve: async () => ({ status: 'RESERVED' }),
    issue: async () => { throw new Error('Tickets temporalmente no disponible'); },
    complete: async () => assert.fail('Falló la emisión'),
    refund: async () => assert.fail('Se reservó aforo')
  }), /temporalmente/);
});
