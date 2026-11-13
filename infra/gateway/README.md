# API Gateway local con Floci

Desde la raíz de EventPass, con Docker Desktop disponible y PowerShell 7:

```powershell
docker compose up -d --wait
docker compose -p eventpass-gateway -f infra/gateway/compose.yaml up -d
$gateway = .\infra\gateway\start-local-gateway.ps1 -SmokeTest
$gateway
```

El script crea una HTTP API con un stage `$default`. Las integraciones HTTP se conectan a los cuatro servicios por la red de Docker. El emulador se publica en el loopback de Windows, en el puerto 4567. Cada ejecución crea un API ID y devuelve la URL para invocarlo. La prueba crea un usuario de prueba local.

```powershell
Invoke-RestMethod "$($gateway.Url)/api/v1/events?page=0&size=1"
```

Si el DNS local no resuelve el dominio de Floci, usar la misma URL por loopback con su encabezado de destino:

```powershell
Invoke-RestMethod "$($gateway.LoopbackEndpoint)/api/v1/events" -Headers @{ Host = $gateway.HostHeader }
```

[routes.json](routes.json) enumera los métodos y rutas de cliente habilitados para esta fase. Auth conserva el inicio y cierre de sesión, y Spring Security valida el JWT y los roles de cada operación. Las llamadas entre servicios siguen usando la red de Docker. El Gateway excluye las rutas internas de autenticación y aforo, y los endpoints Actuator.

Para repetir las pruebas funcionales desde una sola URL, configurar las credenciales locales tal como indica [E2E_TESTS.MD](../../e2e-tests/E2E_TESTS.MD) y ejecutar:

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
