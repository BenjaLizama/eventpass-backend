import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createDependencyCall } from './dependency-call.mjs';

test('abre solo la dependencia fallida y recupera tras una sonda, sin repetir escrituras', async () => {
  let time = 0, requests = 0, status = 503;
  const call = createDependencyCall({ minimum: 2, window: 2, waitMs: 10, now: () => time,
    fetcher: async () => { requests++; return new Response(null, { status }); } });
  for (let i = 0; i < 2; i++) await assert.rejects(call('http://events', '/reserve', 'PUT', {}));
  await assert.rejects(call('http://events', '/reserve', 'PUT', {}), /abierto/);
  assert.equal(requests, 2);
  status = 204;
  await call('http://tickets', '/issue', 'POST', {});
  time = 11;
  await call('http://events', '/reserve', 'PUT', {});
  await call('http://events', '/reserve', 'PUT', {});
  assert.equal(requests, 5);
});

test('rechazos de negocio y permisos no abren el circuito; errores de red sí', async () => {
  let requests = 0;
  const call = createDependencyCall({ minimum: 2, window: 2, fetcher: async () => {
    requests++; return new Response(null, { status: 409 });
  } });
  for (let i = 0; i < 6; i++) await assert.rejects(call('http://events', '/reserve', 'PUT', {}), /409/);
  assert.equal(requests, 6);
  const offline = createDependencyCall({ minimum: 2, window: 2, fetcher: async () => { throw new Error('offline'); } });
  for (let i = 0; i < 2; i++) await assert.rejects(offline('http://auth', '/token', 'POST', {}), /offline/);
  await assert.rejects(offline('http://auth', '/token', 'POST', {}), /abierto/);
});

test('solo permite una sonda simultánea y vuelve a abrir si falla', async () => {
  let time = 0, finish;
  const call = createDependencyCall({ minimum: 1, window: 1, waitMs: 10, now: () => time,
    fetcher: async () => {
      if (time === 0) return new Response(null, { status: 503 });
      return new Promise(resolve => { finish = () => resolve(new Response(null, { status: 503 })); });
    } });
  await assert.rejects(call('http://events', '/reserve', 'PUT', {}));
  time = 11;
  const probe = call('http://events', '/reserve', 'PUT', {});
  await assert.rejects(call('http://events', '/reserve', 'PUT', {}), /abierto/);
  finish(); await assert.rejects(probe);
  await assert.rejects(call('http://events', '/reserve', 'PUT', {}), /abierto/);
});
