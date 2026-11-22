// Circuits are per dependency and per warm Lambda environment, never global across AWS.
// No automatic retry: SQS owns retries and reservations/issuance retain their idempotency keys.
export function createDependencyCall({ fetcher = (...args) => globalThis.fetch(...args), now = Date.now, minimum = 5, window = 10, waitMs = 15000 } = {}) {
  const circuits = new Map();
  return async function call(service, path, method, body, token) {
    const key = service.replace(/\/+$/, '');
    let state = circuits.get(key);
    if (!state) circuits.set(key, state = { samples: [], opened: null, probing: false });
    if (state.opened !== null && (now() - state.opened < waitMs || state.probing)) {
      throw new Error('Dependencia temporalmente no disponible (circuito abierto).');
    }
    const probe = state.opened !== null;
    if (probe) state.probing = true;
    function record(failed) {
      state.samples.push(failed);
      if (state.samples.length > window) state.samples.shift();
      if (probe) {
        if (failed) state.opened = now();
        else { state.opened = null; state.samples = []; }
      } else if (state.samples.length >= minimum && state.samples.filter(Boolean).length / state.samples.length >= 0.5) {
        state.opened = now();
      }
    }
    try {
      let response;
      try {
        response = await fetcher(key + path, {
          method, headers: { 'Content-Type': 'application/json', ...(token && { Authorization: 'Bearer ' + token }) },
          body: JSON.stringify(body), signal: AbortSignal.timeout(4000)
        });
      } catch (error) { record(true); throw error; }
      if (response.status >= 500 || response.status === 429) {
        await response.body?.cancel();
        record(true);
        throw new Error('Dependencia temporalmente no disponible.');
      }
      if (!response.ok) {
        await response.body?.cancel();
        throw new Error(method + ' ' + path + ' devolvió HTTP ' + response.status);
      }
      let result;
      try { if (response.status !== 204) result = (await response.json()).data; }
      catch (error) { record(true); throw error; }
      record(false);
      return result;
    } finally { state.probing = false; }
  };
}
