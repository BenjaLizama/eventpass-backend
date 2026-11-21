import { randomUUID } from 'node:crypto';

export const databases = ['eventpass_auth', 'eventpass_events', 'eventpass_orders'];
const assert = (condition, message) => { if (!condition) throw new Error(message); };

export async function bootstrap({ connectSql, connectRedis, passwords, report = () => {} }) {
  for (const name of databases) {
    assert(typeof passwords[name] === 'string' && /^[A-Za-z0-9]{32,64}$/.test(passwords[name]), 'Invalid generated SQL credential');
  }
  const admin = await connectSql('eventpass_admin', 'postgres');
  try {
    for (const name of databases) {
      report('Preparing private SQL database: ' + name);
      const role = await admin.query('SELECT rolname, rolsuper, rolcreatedb, rolcreaterole FROM pg_roles WHERE rolname = $1', [name]);
      if (!role.rowCount) {
        // Identifiers are a fixed allowlist; passwords come from generated alphanumeric secrets.
        // Never print the SQL text or driver errors, which may include this statement.
        await admin.query(`CREATE ROLE "${name}" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD '${passwords[name]}'`);
      } else {
        assert(!role.rows[0].rolsuper && !role.rows[0].rolcreatedb && !role.rows[0].rolcreaterole, 'Existing SQL role has excessive privileges');
      }
      const database = await admin.query('SELECT pg_get_userbyid(datdba) AS owner FROM pg_database WHERE datname = $1', [name]);
      if (!database.rowCount) {
        // PostgreSQL 17 requires SET membership to create a database for another
        // owner. The managed master already administers these generated roles.
        await admin.query(`GRANT "${name}" TO "eventpass_admin" WITH SET TRUE, INHERIT FALSE`);
        await admin.query(`CREATE DATABASE "${name}" OWNER "${name}"`);
      }
      else assert(database.rows[0].owner === name, 'Existing database has an unexpected owner');
    }
  } finally { await admin.end(); }

  for (const name of databases) {
    const client = await connectSql(name, name);
    try {
      await client.query(`REVOKE CONNECT ON DATABASE "${name}" FROM PUBLIC`);
      await client.query(`GRANT CONNECT ON DATABASE "${name}" TO "${name}"`);
      const tls = await client.query('SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()');
      assert(tls.rows[0]?.ssl === true, 'SQL connection must use TLS');
      await client.query('BEGIN');
      try {
        await client.query('CREATE TEMP TABLE eventpass_connection_check (value text NOT NULL) ON COMMIT DROP');
        await client.query('INSERT INTO eventpass_connection_check VALUES ($1)', ['verified']);
        const result = await client.query('SELECT value FROM eventpass_connection_check');
        assert(result.rows[0]?.value === 'verified', 'SQL read/write check failed');
      } finally { await client.query('ROLLBACK'); }
      report('SQL TLS and read/write verified: ' + name);
    } finally { await client.end(); }
  }
  for (const user of databases) {
    for (const other of databases.filter(name => name !== user)) {
      let rejected = false;
      try {
        const unexpected = await connectSql(user, other);
        await unexpected.end();
      } catch (error) {
        if (error.code !== '42501') throw error;
        rejected = true;
      }
      assert(rejected, 'Cross-service database access must be denied');
    }
  }
  report('SQL isolation verified: six cross-service connections denied');

  const redis = await connectRedis(true);
  const key = 'eventpass:bootstrap-check:' + randomUUID();
  try {
    assert(await redis.ping() === 'PONG', 'Redis ping failed');
    assert(await redis.set(key, 'verified', { EX: 60, NX: true }) === 'OK', 'Redis write failed');
    assert(await redis.get(key) === 'verified', 'Redis read failed');
    const ttl = await redis.ttl(key);
    assert(ttl > 0 && ttl <= 60, 'Redis expiration is missing');
    assert(await redis.del(key) === 1 && await redis.get(key) === null, 'Redis delete failed');
  } finally {
    try { await redis.del(key); } finally { redis.destroy(); }
  }
  let unauthenticated;
  let denied = false;
  try {
    unauthenticated = await connectRedis(false);
    await unauthenticated.ping();
  } catch (error) {
    if (!/NOAUTH|Authentication required/i.test(error.message)) throw error;
    denied = true;
  } finally { unauthenticated?.destroy(); }
  assert(denied, 'Redis must reject unauthenticated requests');
  report('Redis TLS, authentication, expiration, read/write and delete verified');
}
