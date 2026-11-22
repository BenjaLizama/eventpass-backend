import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadCatalog } from './catalog-load.mjs';

const url = 'https://example123.execute-api.us-east-1.amazonaws.com/api/v1/events?page=0&size=100';
test('carga solo catálogo con tráfico acotado, sin reintentos ni mutaciones', async () => {
  const calls = [];
  const result = await loadCatalog({ url, seconds: 1, rate: 5, concurrency: 2, fetcher: async (target, options) => {
    calls.push([target.href, options]); return new Response('{"status":200}');
  } });
  assert.ok(result.requests >= 3 && result.requests <= 6);
  assert.equal(result.failures, 0);
  assert.equal(result.retries, 0);
  assert.ok(calls.every(([,options]) => !options.method && !options.headers && options.redirect === 'error'));
});
test('no acepta otros hosts/rutas o tasas que excedan la cuota; corta ante errores', async () => {
  for (const options of [{ url: 'https://other/api/v1/events' }, { url, rate: 46 }, { url, seconds: 901 },
    { url: url.replace('/events', '/orders') }]) await assert.rejects(loadCatalog(options), /límites/);
  const result = await loadCatalog({ url, seconds: 2, rate: 20, concurrency: 1,
    fetcher: async () => new Response(null, { status: 503 }) });
  assert.equal(result.failures, 3);
  assert.equal(result.requests, 3);
  assert.equal(result.stoppedOnErrors, true);
});

test('registra fallos de transporte sin convertirlos en éxitos ni registrar mensajes sensibles', async () => {
  const result = await loadCatalog({ url, seconds: 2, rate: 20, concurrency: 1, fetcher: async () => {
    throw Object.assign(new Error('never-log-private-value'), { name: 'TimeoutError' });
  } });
  assert.equal(result.failures, 3);
  assert.equal(result.statuses['network-error'], 3);
  assert.equal(result.errorTypes['TimeoutError:unknown'], 3);
  assert.equal(result.retries, 0);
  assert.ok(!JSON.stringify(result).includes('never-log-private-value'));
});
