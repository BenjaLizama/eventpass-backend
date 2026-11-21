import test from 'node:test';
import assert from 'node:assert/strict';
import { bootstrap, databases } from './data-bootstrap/bootstrap-core.mjs';

function fixture({ crossAccess = false, tls = true, unauthenticatedRedis = false } = {}) {
  const roles = new Set();
  const stores = new Set();
  const statements = [];
  const reports = [];
  const keys = new Map();
  let denied = 0;
  const passwords = Object.fromEntries(databases.map(name => [name, 'A'.repeat(40)]));
  const options = {
    passwords, report: message => reports.push(message),
    connectSql: async (user, database) => {
      if (user !== 'eventpass_admin' && user !== database && !crossAccess) {
        denied++;
        throw Object.assign(new Error('permission denied'), { code: '42501' });
      }
      return { end: async () => {}, query: async (sql, values) => {
        statements.push(sql);
        if (sql.startsWith('SELECT rolname')) return {
          rowCount: roles.has(values[0]) ? 1 : 0, rows: [{ rolsuper: false, rolcreatedb: false, rolcreaterole: false }]
        };
        if (sql.startsWith('CREATE ROLE')) { roles.add(sql.match(/"([^"]+)"/)[1]); return {}; }
        if (sql.startsWith('SELECT pg_get_userbyid')) return { rowCount: stores.has(values[0]) ? 1 : 0, rows: [{ owner: values[0] }] };
        if (sql.startsWith('CREATE DATABASE')) { stores.add(sql.match(/"([^"]+)"/)[1]); return {}; }
        if (sql.includes('pg_stat_ssl')) return { rows: [{ ssl: tls }] };
        if (sql === 'SELECT value FROM eventpass_connection_check') return { rows: [{ value: 'verified' }] };
        return {};
      } };
    },
    connectRedis: async authenticated => ({
      ping: async () => {
        if (!authenticated && !unauthenticatedRedis) throw new Error('NOAUTH Authentication required');
        return 'PONG';
      },
      set: async (key, value, settings) => {
        assert.deepEqual(settings, { EX: 60, NX: true });
        assert.match(key, /^eventpass:bootstrap-check:/);
        keys.set(key, value); return 'OK';
      },
      get: async key => keys.get(key) ?? null,
      ttl: async () => 60,
      del: async key => keys.delete(key) ? 1 : 0,
      destroy: () => {}
    })
  };
  return { options, roles, stores, statements, reports, keys, denied: () => denied };
}

test('prepares separate databases, verifies six denied connections and removes its Redis test key', async () => {
  const f = fixture();
  await bootstrap(f.options);
  assert.equal(f.roles.size, 3);
  assert.equal(f.stores.size, 3);
  assert.equal(f.denied(), 6);
  assert.equal(f.keys.size, 0);
  assert.ok(!f.reports.some(message => message.includes('A'.repeat(40))));
});

test('repeating bootstrap does not recreate databases or rotate existing passwords', async () => {
  const f = fixture();
  await bootstrap(f.options);
  await bootstrap(f.options);
  assert.equal(f.statements.filter(sql => sql.startsWith('CREATE ROLE')).length, 3);
  assert.equal(f.statements.filter(sql => sql.startsWith('CREATE DATABASE')).length, 3);
  assert.ok(!f.statements.some(sql => sql.startsWith('ALTER ROLE')));
});

test('rejects plaintext SQL, cross-service database access and Redis without authentication', async () => {
  for (const [options, message] of [
    [{ tls: false }, /must use TLS/],
    [{ crossAccess: true }, /must be denied/],
    [{ unauthenticatedRedis: true }, /reject unauthenticated/]
  ]) await assert.rejects(bootstrap(fixture(options).options), message);
});

test('invalid generated credentials fail before connecting to any database', async () => {
  let connected = false;
  await assert.rejects(bootstrap({ passwords: {}, connectSql: async () => { connected = true; } }), /Invalid generated/);
  assert.equal(connected, false);
});
