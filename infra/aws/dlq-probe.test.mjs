import test from 'node:test';
import assert from 'node:assert/strict';
import { createFaultFetch, buildProbeEntry } from './dlq-probe.mjs';

const id = '123e4567-e89b-42d3-a456-426614174000';
test('fault is restricted to ticket issuance for the selected order, after reservation', async () => {
  const calls = [];
  const fetch = createFaultFetch(async (url, options) => { calls.push([url, options]); return 'ok'; }, id);
  const body = JSON.stringify({ orderId: id });
  assert.equal(await fetch('http://private/api/v1/auth/service-token', { body }), 'ok');
  assert.equal(await fetch('http://private/api/v1/events/x/reservations/y', { body }), 'ok');
  assert.equal(await fetch('http://private/internal/tickets/issue', { body: '{"orderId":"other"}' }), 'ok');
  await assert.rejects(fetch('http://private/internal/tickets/issue', { body }), /EVENTPASS_DLQ_PROBE/);
  assert.equal(calls.length, 3);
});

test('cannot inject source into the temporary entrypoint and does not affect ordinary reads', async () => {
  assert.throws(() => buildProbeEntry("';throw Error('bad')//"), /inválido/);
  assert.match(buildProbeEntry(id), /purchase-handler.mjs/);
  assert.equal(await createFaultFetch(async () => 'ok', id)('http://private/api/v1/tickets/me'), 'ok');
});
