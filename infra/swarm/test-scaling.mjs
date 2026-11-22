import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { setTimeout as delay } from 'node:timers/promises';
import { mkdirSync, writeFileSync } from 'node:fs';

const exec = promisify(execFile);
const compose = ['compose', '-p', 'eventpass-swarm-lab', '-f', 'infra/swarm/compose.lab.yaml', 'exec', '-T', 'manager', 'docker'];
const docker = async (...args) => (await exec('docker', [...compose, ...args], { timeout: 30000 })).stdout.trim();
const url = 'http://127.0.0.1:18082/api/v1/events?page=0&size=1';

async function load(label, concurrency, operation) {
  const latencies = [], statuses = {};
  let stop = false;
  const start = performance.now();
  const workers = Array.from({ length: concurrency }, async () => {
    while (!stop) {
      const begin = performance.now();
      let status;
      try {
        // Each sample is an independent HTTP request, like a new curl invocation.
        // No transparent retries: reused TCP sockets can be reset when Swarm removes
        // a replica, which is a different test from accepting new requests.
        const response = await fetch(url, { headers: { Connection: 'close' }, signal: AbortSignal.timeout(5000) });
        status = response.status;
        const body = await response.json();
        if (response.ok && body.status !== 200) status = 'invalid-body';
      } catch { status = 'network-error'; }
      statuses[status] = (statuses[status] ?? 0) + 1;
      latencies.push(performance.now() - begin);
      await delay(200);
    }
  });
  try { await operation(); }
  finally { stop = true; await Promise.all(workers); }
  latencies.sort((a, b) => a - b);
  const result = { phase: label, concurrency, requests: latencies.length, statuses,
    seconds: Number(((performance.now() - start) / 1000).toFixed(2)),
    p95Ms: Number(latencies[Math.min(latencies.length - 1, Math.floor(latencies.length * 0.95))].toFixed(2)),
    maxMs: Number(latencies.at(-1).toFixed(2)) };
  console.log(JSON.stringify(result));
  if (statuses[200] !== latencies.length) throw Object.assign(new Error('Hubo errores durante ' + label), { result });
  return result;
}

async function waitReplicas(count) {
  const deadline = Date.now() + 420000;
  while (Date.now() < deadline) {
    const status = await docker('service', 'ls', '--filter', 'name=eventpass_ms-events', '--format', '{{.Replicas}}');
    const update = await docker('service', 'inspect', 'eventpass_ms-events', '--format', '{{if .UpdateStatus}}{{.UpdateStatus.State}}{{end}}');
    if (status === `${count}/${count}` && !['updating', 'rollback_started'].includes(update)) {
      // Running alone is insufficient: both replicas must have passed their Docker health check.
      const workerArgs = compose.map(x => x === 'manager' ? 'worker' : x);
      const ps = (await exec('docker', [...workerArgs, 'ps', '--filter', 'label=com.docker.swarm.service.name=eventpass_ms-events', '--format', '{{.Status}}'], { timeout: 30000 })).stdout.trim().split('\n');
      if (ps.length === count && ps.every(x => x.includes('(healthy)'))) return;
    }
    await delay(2000);
  }
  throw new Error('Las réplicas no convergieron a ' + count);
}

const report = { checkedAt: new Date().toISOString(), platform: 'Docker Swarm, two engines on one PC', phases: [] };
report.connections = 'Independent HTTP connections; no retries. Persistent-connection draining is not covered.';
let restore = false;
mkdirSync('.local/swarm', { recursive: true });
try {
  const current = await docker('service', 'inspect', 'eventpass_ms-events', '--format', '{{.Spec.Mode.Replicated.Replicas}}');
  if (current !== '1') throw new Error('Primero ejecuta test-swarm.ps1: se requiere Eventos con una réplica.');
  await waitReplicas(1);
  restore = true;
  report.phases.push(await load('baseline-one-replica', 2, () => delay(6000)));
  report.phases.push(await load('scale-out-under-load', 4, async () => {
    await docker('service', 'scale', '--detach=true', 'eventpass_ms-events=2'); await waitReplicas(2);
  }));
  report.phases.push(await load('steady-two-replicas', 8, () => delay(6000)));
  report.phases.push(await load('scale-in-under-load', 4, async () => {
    await docker('service', 'scale', '--detach=true', 'eventpass_ms-events=1'); await waitReplicas(1);
  }));
  report.success = true;
} catch (error) {
  report.success = false;
  report.error = error.message;
  if (error.result) report.phases.push(error.result);
  throw error;
} finally {
  // Restore the known starting capacity even when a request fails.
  if (restore) {
    await docker('service', 'scale', '--detach=true', 'eventpass_ms-events=1');
    await waitReplicas(1);
  }
  writeFileSync('.local/swarm/scaling-load.json', JSON.stringify(report, null, 2) + '\n');
}
