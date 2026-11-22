import { writeFileSync } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export async function loadCatalog({ url, seconds = 600, rate = 35, concurrency = 16, stop = () => false, fetcher = fetch }) {
  const target = new URL(url);
  if (!/^https:\/\/[a-z0-9]+\.execute-api\.us-east-1\.amazonaws\.com$/.test(target.origin) ||
      target.pathname !== '/api/v1/events' || seconds < 1 || seconds > 900 || rate < 1 || rate > 45 || concurrency < 1 || concurrency > 24) {
    throw new Error('Carga fuera del Gateway, ruta o límites autorizados.');
  }
  const start = performance.now(), end = start + seconds * 1000;
  const timings = [], statuses = {}, errorTypes = {};
  let next = start, failures = 0, halted = false;
  await Promise.all(Array.from({ length: concurrency }, async () => {
    while (performance.now() < end && !stop() && !halted) {
      const slot = Math.max(next, performance.now());
      next = slot + 1000 / rate;
      await delay(Math.max(0, slot - performance.now()));
      if (performance.now() >= end || stop() || halted) break;
      const begin = performance.now();
      let status;
      try {
        const response = await fetcher(target, { redirect: 'error', signal: AbortSignal.timeout(25000) });
        status = response.status;
        if (response.ok && (await response.json()).status !== 200) status = 'invalid-body';
        else if (!response.ok) await response.body?.cancel();
      } catch (error) {
        status = 'network-error';
        const kind = String(error?.name ?? 'Error') + ':' + String(error?.cause?.code ?? error?.code ?? 'unknown');
        errorTypes[kind] = (errorTypes[kind] ?? 0) + 1;
      }
      statuses[status] = (statuses[status] ?? 0) + 1;
      timings.push(performance.now() - begin);
      if (status !== 200 && ++failures >= 3) halted = true;
    }
  }));
  timings.sort((a, b) => a - b);
  const elapsedSeconds = (performance.now() - start) / 1000;
  return { requests: timings.length, failures, statuses, errorTypes, offeredRate: rate, concurrency,
    seconds: Number(elapsedSeconds.toFixed(2)),
    completedRps: Number((timings.length / elapsedSeconds).toFixed(2)),
    p95Ms: timings.length ? Number(timings[Math.min(timings.length - 1, Math.floor(timings.length * .95))].toFixed(2)) : null,
    p99Ms: timings.length ? Number(timings[Math.min(timings.length - 1, Math.floor(timings.length * .99))].toFixed(2)) : null,
    maxMs: timings.length ? Number(timings.at(-1).toFixed(2)) : null,
    over8Seconds: timings.filter(value => value > 8000).length,
    requestTimeoutMs: 25000,
    stoppedOnErrors: halted, retries: 0 };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [url, seconds, rate, report] = process.argv.slice(2);
  let stopped = false;
  process.stdin.setEncoding('utf8');
  process.stdin.on('data', () => { stopped = true; });
  process.stdin.resume();
  try {
    const result = await loadCatalog({ url, seconds: Number(seconds), rate: Number(rate), concurrency: 24, stop: () => stopped });
    writeFileSync(report, JSON.stringify(result, null, 2) + '\n');
    console.log(JSON.stringify(result));
    if (result.failures || !result.requests) process.exitCode = 1;
  } finally { process.stdin.destroy(); }
}
