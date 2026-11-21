import { mkdirSync, readFileSync, writeFileSync, copyFileSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const uuid = /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i;
export function createFaultFetch(original, orderId) {
  if (!uuid.test(orderId)) throw new Error('ID de prueba inválido.');
  return async (url, options) => {
    let body;
    try { body = JSON.parse(options?.body); } catch { /* Other requests are unchanged. */ }
    if (new URL(url).pathname === '/internal/tickets/issue' && body?.orderId === orderId) {
      throw new Error('EVENTPASS_DLQ_PROBE ' + orderId);
    }
    return original(url, options);
  };
}

export function buildProbeEntry(orderId) {
  if (!uuid.test(orderId)) throw new Error('ID de prueba inválido.');
  return `import { handler } from './purchase-handler.mjs';
import { createFaultFetch } from './dlq-probe.mjs';
globalThis.fetch = createFaultFetch(globalThis.fetch, '${orderId}');
export { handler };
`;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [orderId, directory] = process.argv.slice(2);
  const entry = buildProbeEntry(orderId);
  const here = fileURLToPath(new URL('.', import.meta.url));
  mkdirSync(directory, { recursive: true });
  writeFileSync(join(directory, 'index.mjs'), entry);
  copyFileSync(join(here, '../purchase-lambda/index.mjs'), join(directory, 'purchase-handler.mjs'));
  copyFileSync(join(here, '../purchase-lambda/process-order.mjs'), join(directory, 'process-order.mjs'));
  writeFileSync(join(directory, 'dlq-probe.mjs'), readFileSync(fileURLToPath(import.meta.url)));
}
