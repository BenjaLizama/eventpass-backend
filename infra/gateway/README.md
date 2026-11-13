# API Gateway local con Floci

Desde la raíz de EventPass, con Docker Desktop disponible y PowerShell 7:

```powershell
docker compose -f docker-compose.yml -f infra/gateway/compose.private.yaml up -d --wait
docker compose -p eventpass-gateway -f infra/gateway/compose.yaml up -d
$gateway = .\infra\gateway\start-local-gateway.ps1 -SmokeTest
$gateway
```

Esta variante retira las publicaciones de los puertos de los microservicios, bases de datos, Redis y LocalStack. Floci mantiene el acceso desde Windows en el puerto 4567. Para comprobar que Eventos ya no es accesible de forma directa, ejecuta `Test-NetConnection 127.0.0.1 -Port 8082 -InformationLevel Quiet`. Debe devolver `False`.

Para volver al modo de desarrollo con puertos directos, ejecuta `docker compose up -d --wait` desde la raíz.

## Swagger común

Después de crear `$gateway`, levanta la documentación en el mismo proyecto de Docker:

```powershell
$env:EVENTPASS_API_ID = $gateway.ApiId
docker compose -p eventpass-gateway -f infra/gateway/compose.yaml -f infra/gateway/compose.swagger.yaml up -d
```

Abre [Swagger de EventPass](http://127.0.0.1:8090/). La definición combina los esquemas de los cuatro servicios y publica las operaciones de `routes.json`. El botón **Execute** llama al mismo puerto que sirve Swagger, que reenvía la petición a Floci por la red de Docker. La interfaz carga sus recursos visuales desde jsDelivr.

Prueba `GET /api/v1/events` con **Try it out → Execute**. Para consultar órdenes o tickets, ejecuta primero `POST /api/v1/auth/login` y pega el `access_token` en **Authorize**. Si recreas el Gateway y cambia el ApiId, repite las dos órdenes de arranque de Swagger.

### Demo de compra en Swagger

1. Con un token **ADMIN**, crea un recinto, copia su `data.id` al `venueId` del ejemplo de creación de evento y publica el evento usando el ID devuelto. El formulario de creación propone fechas futuras.
2. Registra al comprador de prueba e inicia sesión. En **Authorize**, reemplaza el token anterior por el `access_token` del comprador.
3. En el catálogo, abre el evento y copia su ID y el de una localidad con cupos. Pégalos en el ejemplo de `POST /api/v1/orders`. Usa el ID de la orden devuelta en `POST /api/v1/orders/{id}/payment`.
4. Consulta `GET /api/v1/tickets/me` hasta que aparezca el ticket. Para validarlo, usa su código y cambia a un token **STAFF** antes de ejecutar `PATCH /api/v1/tickets/{ticketCode}/use`.

Los UUID de ejemplo ilustran el formato. Cada formulario indica de dónde copiar el valor de tu ejecución. Los correos de ejemplo son para cuentas de prueba y deben ser únicos al registrar usuarios.

El script crea una HTTP API con un stage `$default`. Las integraciones HTTP se conectan a los cuatro servicios por la red de Docker. El emulador se publica en el loopback de Windows, en el puerto 4567. Cada ejecución crea un API ID y devuelve la URL para invocarlo. La prueba crea un usuario de prueba local.

```powershell
Invoke-RestMethod "$($gateway.Url)/api/v1/events?page=0&size=1"
```

Si el DNS local no resuelve el dominio de Floci, usar la misma URL por loopback con su encabezado de destino:

```powershell
Invoke-RestMethod "$($gateway.LoopbackEndpoint)/api/v1/events" -Headers @{ Host = $gateway.HostHeader }
```

[routes.json](routes.json) enumera los métodos y rutas de cliente habilitados para esta fase. Auth conserva el inicio y cierre de sesión, y Spring Security valida el JWT y los roles de cada operación. Las llamadas entre servicios siguen usando la red de Docker. El Gateway excluye las rutas internas de autenticación y aforo, y los endpoints Actuator.

Para repetir las pruebas funcionales desde una sola URL, define E2E_ADMIN_EMAIL y E2E_ADMIN_PASSWORD con las credenciales del administrador local y ejecuta:

```powershell
$env:AUTH_BASE_URL = $gateway.Url
$env:EVENTS_BASE_URL = $gateway.Url
$env:ORDERS_BASE_URL = $gateway.Url
$env:TICKETS_BASE_URL = $gateway.Url
.\ms-auth\mvnw.cmd -f e2e-tests/pom.xml '-Dit.test=*,!PlatformHealthIT,!OrderItemCapacityIT' verify
```

`PlatformHealthIT` comprueba Actuator y `OrderItemCapacityIT` prueba las operaciones internas de aforo. Ambas se ejecutan contra los servicios directos. En Learner Lab, queda por configurar la integración privada mediante VPC Link y un ALB interno. [API Gateway en Floci](https://floci.io/floci/services/api-gateway/) · [Integración privada de HTTP API en AWS](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-private.html)

Para detener Floci y eliminar las APIs locales que creó:

```powershell
docker compose -p eventpass-gateway -f infra/gateway/compose.yaml down
```
