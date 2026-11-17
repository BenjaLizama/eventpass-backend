# Arranque con bases PostgreSQL vacías

Auth, Eventos y Órdenes aplican las migraciones de `src/main/resources/db/migration` al arrancar con el perfil `prod`. Flyway crea las tablas y registra las versiones aplicadas en `flyway_schema_history`. Después Hibernate valida el esquema. La migración de Órdenes incluye la outbox y todos los estados de la compra asíncrona.

En Aurora PostgreSQL, crea una base por servicio y configura sus variables `AUTH_DB_*`, `EVENTS_DB_*` y `ORDERS_DB_*`. La identidad que ejecuta la migración necesita permisos de creación en el esquema. Para una base con tablas anteriores y sin historial Flyway, revisa el esquema y prepara un baseline explícito antes de habilitar `prod`. El perfil `dev` conserva el arranque actual.

En AWS, Eventos, Órdenes y Tickets leen la región de `AWS_REGION` y obtienen credenciales mediante la cadena del SDK. En ECS, configura los permisos de SQS en el rol de tarea. En la prueba local, Compose proporciona el endpoint de LocalStack y credenciales de prueba.

## Verificación local aislada

Ejecuta desde la raíz, con Docker Desktop listo y un `.env` de desarrollo. Usa un nombre de proyecto nuevo para que Docker cree volúmenes separados.

```powershell
$compose = @('compose', '-p', 'eventpass-schema-verify',
    '-f', 'docker-compose.yml',
    '-f', 'infra/gateway/compose.private.yaml',
    '-f', 'infra/database/compose.verify.yaml')
docker @compose up -d --build --wait --wait-timeout 240
```

Los cuatro servicios se publican solo en loopback, en los puertos `18081` a `18084`. Puedes apuntar las pruebas E2E a esas direcciones mediante `AUTH_BASE_URL`, `EVENTS_BASE_URL`, `ORDERS_BASE_URL` y `TICKETS_BASE_URL`. Usa las credenciales de prueba del administrador y del cliente interno del entorno que acabas de levantar.

```powershell
$env:AUTH_BASE_URL = 'http://127.0.0.1:18081'
$env:EVENTS_BASE_URL = 'http://127.0.0.1:18082'
$env:ORDERS_BASE_URL = 'http://127.0.0.1:18083'
$env:TICKETS_BASE_URL = 'http://127.0.0.1:18084'
$env:E2E_ASYNC_PURCHASE = 'false'
.\ms-auth\mvnw.cmd -B -f e2e-tests/pom.xml verify
```

Esta verificación usa LocalStack y la compra de referencia para probar los perfiles de producción contra bases recién creadas. La compra asíncrona con Floci se ejecuta mediante [su propio entorno](../purchase-lambda/README.md).

Para comprobar el historial en cada base:

```powershell
foreach ($database in 'auth','events','orders') {
    docker @compose exec -T "postgres-$database" sh -c '
        psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, success FROM flyway_schema_history;"
    '
}
```

Al terminar, `docker @compose down` detiene la plataforma de prueba y conserva los volúmenes. Con un nombre de proyecto distinto puedes repetir el arranque desde cero.

## Tickets con DynamoDB

Tickets puede arrancar con el perfil adicional `dynamodb`. La API conserva sus rutas y permisos. La tabla usa una clave por código de ticket y otra por ítem e índice de emisión. DynamoDB escribe ambas en una transacción. La validación en puerta solo cambia el ticket a `USED` si todavía está `ACTIVE`. El índice `by-user` sirve para listar los tickets del comprador y puede tardar en reflejar una actualización.

Para una verificación local nueva, usa un nombre de proyecto distinto y añade `infra/database/compose.dynamodb.yaml` como último archivo de Compose. Inicializa la tabla antes de arrancar los microservicios:

```powershell
$compose = @('compose', '-p', 'eventpass-dynamo-verify',
    '-f', 'docker-compose.yml', '-f', 'infra/gateway/compose.private.yaml',
    '-f', 'infra/database/compose.verify.yaml', '-f', 'infra/database/compose.dynamodb.yaml')
docker @compose up -d --wait localstack dynamodb
docker @compose exec -T localstack aws --endpoint-url http://dynamodb:8000 dynamodb create-table --cli-input-json file:///etc/eventpass/tickets-table.json
docker @compose up -d --build --wait --wait-timeout 240
```

Usa las variables de las E2E anteriores y añade `$env:E2E_CHECK_TICKET_REPLAY = 'true'` para repetir la emisión y comprobar que se mantienen los mismos códigos. La prueba de compra también intenta usar un ticket con dos solicitudes concurrentes y exige una respuesta `200` y otra `409`. DynamoDB Local se ejecuta en memoria en esta verificación.

En AWS, crea la tabla con `tickets-table.json` en la misma región que Tickets, configura `SPRING_PROFILES_ACTIVE=prod,dynamodb`, `AWS_REGION` y `TICKETS_DYNAMODB_TABLE`, y deja `TICKETS_DYNAMODB_ENDPOINT` vacío. El rol de tarea de Tickets debe poder consultar el índice y leer, insertar y actualizar los ítems de la tabla. Activar el perfil apunta a una tabla distinta. Si la entrega requiere conservar tickets de MongoDB, hace falta migrar esos datos antes del cambio.
