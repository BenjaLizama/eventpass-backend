import { readFileSync } from 'node:fs';
import pg from 'pg';
import { createClient } from 'redis';
import { bootstrap } from './bootstrap-core.mjs';

const passwords = {
  eventpass_admin: process.env.MASTER_DB_PASSWORD,
  eventpass_auth: process.env.AUTH_DB_PASSWORD,
  eventpass_events: process.env.EVENTS_DB_PASSWORD,
  eventpass_orders: process.env.ORDERS_DB_PASSWORD
};
try {
  const ca = readFileSync('/app/rds-ca.pem', 'utf8');
  await bootstrap({
    passwords,
    report: message => console.log(message),
    connectSql: async (user, database) => {
      const client = new pg.Client({ host: process.env.DATABASE_HOST, port: 5432, user, database,
        password: passwords[user], ssl: { ca, rejectUnauthorized: true },
        connectionTimeoutMillis: 10000, query_timeout: 10000 });
      client.on('error', () => {});
      try { await client.connect(); return client; }
      catch (error) { await client.end().catch(() => {}); throw error; }
    },
    connectRedis: async authenticated => {
      const client = createClient({
        socket: { host: process.env.REDIS_HOST, port: 6379, tls: true,
          servername: process.env.REDIS_HOST, rejectUnauthorized: true,
          connectTimeout: 10000, reconnectStrategy: false },
        password: authenticated ? process.env.REDIS_PASSWORD : undefined,
        disableClientInfo: true, RESP: 2
      });
      client.on('error', () => {});
      try { await client.connect(); return client; }
      catch (error) { if (client.isOpen) client.destroy(); throw error; }
    }
  });
  console.log('EventPass data bootstrap completed');
} catch (error) {
  // Driver messages and stacks may contain SQL, endpoints or credentials.
  const code = /^[A-Z0-9_]+$/.test(error.code ?? '') ? error.code : 'CHECK_FAILED';
  console.error('EventPass data bootstrap failed; code=' + code);
  process.exitCode = 1;
}
