import { createServer, request } from 'node:http';
import { readFileSync } from 'node:fs';

const apiId = process.env.EVENTPASS_API_ID;
if (!/^[a-z0-9]+$/.test(apiId ?? '')) throw new Error('ApiId de Floci inválido');

const routes = JSON.parse(readFileSync(new URL('./routes.json', import.meta.url)));
const groups = ['Auth', 'Eventos', 'Órdenes', 'Tickets'];
const demoAccount = { email: 'comprador.demo@example.com', password: 'EventPassDemo123!' };
const referenceId = '123e4567-e89b-42d3-a456-426614174000';
const instructions = {
  'POST /api/v1/auth/register': 'Registra el comprador de prueba. Si el correo ya existe, inicia sesión o cambia el correo.',
  'POST /api/v1/auth/login': 'Usa las credenciales del comprador que registraste o las de tu cuenta local. Copia access_token a Authorize.',
  'POST /api/v1/auth/refresh': 'Pega el refresh_token devuelto por login en refreshToken.',
  'POST /api/v1/auth/logout': 'Ejecuta al final. El token de esta sesión dejará de funcionar.',
  'PATCH /api/v1/auth/me': 'Usa un correo de prueba que todavía no esté registrado.',
  'POST /api/v1/admin/users/staff': 'Usa un token ADMIN y un correo libre. Luego inicia sesión con la cuenta STAFF para validar un ticket.',
  'POST /api/v1/admin/users/organizer': 'Usa un token ADMIN y un correo libre.',
  'POST /api/v1/venues': 'Usa ADMIN u ORGANIZER y guarda data.id para crear el evento.',
  'POST /api/v1/events': 'Usa ADMIN u ORGANIZER. Sustituye venueId por el ID devuelto al crear un recinto.',
  'PUT /api/v1/events/{id}': 'Usa el ID de un evento en borrador que puedas editar, antes de publicarlo.',
  'PATCH /api/v1/events/{id}/publish': 'Usa el ID del borrador creado en POST /api/v1/events.',
  'PATCH /api/v1/events/{id}/cancel': 'Usa un evento de prueba y ejecuta después de la demo de compra.',
  'GET /api/v1/events/my-events': 'Usa el token ADMIN u ORGANIZER con el que creaste el evento.',
  'POST /api/v1/orders': 'Usa un token CUSTOMER. Copia eventId y ticketCategoryId de un evento publicado con cupos. Guarda data.id.',
  'PATCH /api/v1/orders/{id}/cancel': 'Crea una orden de prueba adicional y cancélala antes de pagarla.',
  'POST /api/v1/orders/{id}/payment': 'Usa el ID de una orden pendiente de tu comprador. CARD simula el pago. Después consulta Mis tickets.',
  'GET /api/v1/tickets/me': 'Usa el token del comprador. Después del pago, vuelve a consultar hasta que aparezca el ticket.',
  'GET /api/v1/tickets/{ticketCode}': 'Usa el token del comprador y copia el código desde GET /api/v1/tickets/me.',
  'PATCH /api/v1/tickets/{ticketCode}/use': 'Usa un token STAFF y el código de un ticket activo. El primer uso cambia su estado a USED.'
};

function bodyExamples() {
  const startDate = new Date(Date.now() + 7 * 86400000);
  const endDate = new Date(startDate.getTime() + 2 * 3600000);
  return {
    'POST /api/v1/auth/register': demoAccount,
    'POST /api/v1/auth/login': demoAccount,
    'POST /api/v1/auth/refresh': { refreshToken: 'PEGA_AQUÍ_EL_REFRESH_TOKEN' },
    'PATCH /api/v1/auth/me': { email: 'comprador.actualizado@example.com' },
    'POST /api/v1/admin/users/staff': { ...demoAccount, email: 'staff.demo@example.com' },
    'POST /api/v1/admin/users/organizer': { ...demoAccount, email: 'organizador.demo@example.com' },
    'POST /api/v1/venues': { name: 'Recinto Demo EventPass', address: 'Avenida de Prueba 123', city: 'Santiago', country: 'Chile', capacity: 100 },
    'POST /api/v1/events': {
      title: 'Concierto Demo EventPass', description: 'Evento de prueba', category: 'CONCERT',
      startDate: startDate.toISOString(), endDate: endDate.toISOString(), venueId: referenceId,
      ticketCategories: [{ name: 'General', price: 10000, totalCapacity: 10, maxPerUser: 2 }]
    },
    'PUT /api/v1/events/{id}': { title: 'Concierto Demo actualizado', description: 'Descripción actualizada' },
    'POST /api/v1/orders': { eventId: referenceId, ticketCategoryId: referenceId, quantity: 1 },
    'POST /api/v1/orders/{id}/payment': { paymentMethod: 'CARD' }
  };
}
const operations = Object.entries(routes).flatMap(([service, keys], index) =>
  keys.map(key => {
    const [method, path] = key.split(' ');
    const pattern = path.split('/').map(segment => segment.startsWith('{')
      ? '[^/]+'
      : segment.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('/');
    return { service, method, path, group: groups[index], pattern: new RegExp('^' + pattern + '$') };
  })
);

async function definition() {
  const specs = await Promise.all(Object.keys(routes).map(async service => {
    const response = await fetch('http://' + service + '/v3/api-docs', { signal: AbortSignal.timeout(10000) });
    if (!response.ok) throw new Error(service + ' devolvió HTTP ' + response.status);
    const spec = await response.json();
    const schemas = spec.components?.schemas ?? {};
    const prefix = service.split(':')[0] + '_';
    const rename = key => prefix + key;
    return [service, {
      paths: rewriteRefs(spec.paths, rename),
      security: spec.security,
      schemas: Object.fromEntries(Object.entries(schemas).map(([key, value]) => [rename(key), rewriteRefs(value, rename)]))
    }];
  }));

  const sources = Object.fromEntries(specs);
  const paths = {};
  const examples = bodyExamples();
  for (const { service, method, path, group } of operations) {
    const source = sources[service].paths[path];
    const operation = source?.[method.toLowerCase()];
    if (!operation) throw new Error('Falta documentación para ' + method + ' ' + path);
    paths[path] ??= { parameters: expandPageable(source.parameters) };
    const { servers, ...details } = operation;
    const key = method + ' ' + path;
    const instruction = instructions[key];
    const jsonBody = details.requestBody?.content?.['application/json'];
    if (jsonBody && examples[key]) {
      delete jsonBody.examples;
      jsonBody.example = examples[key];
    }
    paths[path][method.toLowerCase()] = {
      ...details,
      description: [instruction, details.description].filter(Boolean).join('\n\n'),
      parameters: expandPageable(operation.parameters),
      security: operation.security ?? sources[service].security,
      tags: [group]
    };
  }

  return {
    openapi: '3.0.3',
    info: { title: 'EventPass · API Gateway', version: '1.0', description: 'API pública a través de Floci. Gateway: ' + apiId },
    servers: [{ url: '/', description: 'API Gateway local vía Floci' }],
    paths,
    components: {
      schemas: Object.assign({}, ...specs.map(([, spec]) => spec.schemas)),
      securitySchemes: { bearerAuth: { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' } }
    }
  };
}

function rewriteRefs(value, rename) {
  if (Array.isArray(value)) return value.map(item => rewriteRefs(item, rename));
  if (value === null || typeof value !== 'object') return value;
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [
    key, key === '$ref' && typeof item === 'string'
      ? item.replace(/^#\/components\/schemas\/(.+)$/, (_, name) => '#/components/schemas/' + rename(name))
      : rewriteRefs(item, rename)
  ]));
}

function expandPageable(parameters) {
  return parameters?.flatMap(parameter => {
    if (parameter.in === 'path') {
      const source = {
        eventId: 'el id del evento de GET /api/v1/events',
        ticketCategoryId: 'el id de ticketCategories del evento',
        ticketCode: 'el código de GET /api/v1/tickets/me'
      }[parameter.name] ?? 'el data.id del recurso de esta ruta (recinto, evento u orden)';
      return [{
        ...parameter,
        description: 'Copia ' + source + '.',
        example: referenceId
      }];
    }
    if (parameter.in !== 'query' || parameter.name !== 'pageable') return [parameter];
    return [
      { in: 'query', name: 'page', description: 'Página, desde 0.', example: 0, schema: { type: 'integer', minimum: 0 } },
      { in: 'query', name: 'size', description: 'Cantidad de resultados por página.', example: 5, schema: { type: 'integer', minimum: 1 } }
    ];
  });
}

const page = `<!doctype html>
<html lang="es"><meta charset="utf-8"><title>EventPass · API Gateway</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.29.5/swagger-ui.css">
<div id="swagger-ui"></div>
<script src="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.29.5/swagger-ui-bundle.js"></script>
<script>SwaggerUIBundle({url:'/openapi.json',dom_id:'#swagger-ui',validatorUrl:null,docExpansion:'none'});</script>
</html>`;

createServer(async (req, res) => {
  const path = req.url.split('?')[0];
  if (req.method === 'GET' && path === '/') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' }).end(page);
    return;
  }
  if (req.method === 'GET' && path === '/openapi.json') {
    try {
      const spec = await definition();
      res.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify(spec));
    } catch (error) {
      res.writeHead(502).end(error.message);
    }
    return;
  }
  if (!operations.some(operation => operation.method === req.method && operation.pattern.test(path))) {
    res.writeHead(404).end();
    return;
  }
  const headers = { host: apiId + '.execute-api.localhost.floci.io' };
  for (const key of ['authorization', 'accept', 'content-type', 'content-length']) {
    if (req.headers[key]) headers[key] = req.headers[key];
  }
  const upstream = request({ hostname: 'floci-gateway', port: 4566, path: req.url, method: req.method, headers, timeout: 15000 }, response => {
    res.writeHead(response.statusCode, { 'Content-Type': response.headers['content-type'] ?? 'application/json' });
    response.pipe(res);
  });
  upstream.on('timeout', () => upstream.destroy(new Error('Floci no respondió a tiempo')));
  upstream.on('error', () => res.writeHead(502).end('No se pudo conectar con Floci'));
  req.pipe(upstream);
}).listen(8090, '0.0.0.0');
