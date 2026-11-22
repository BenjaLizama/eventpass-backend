# EventPass: guía de desarrollo, despliegue y entrega EP3

Guía única del proyecto. Los comandos se ejecutan desde la raíz en PowerShell 7, con Docker Desktop en modo Linux. Los archivos de código, configuración y pruebas permanecen en sus carpetas; las instrucciones se mantienen aquí.

## 1. Qué construimos y cómo se conecta

EventPass gestiona usuarios, eventos, compras simuladas y entradas. El backend sigue siendo Java 21 con Spring Boot.

```text
Cliente / Postman / Swagger
          |
     API Gateway -- Lambda Java: sesión y roles
          |
       VPC Link
          |
      ALB interno
          |
  ECS: Auth / Eventos / Órdenes / Tickets
                          |
                  SQS compra pendiente
                          |
                 Lambda procesar compra
                          |
           reservar aforo -> emitir -> confirmar
```

| Componente | Responsabilidad |
| --- | --- |
| Auth | Usuarios, roles, ACCESS/REFRESH, sesiones Redis y credenciales de servicio. |
| Eventos | Recintos, catálogo, propiedad del organizador y reservas de aforo. |
| Órdenes | Órdenes propias, pago simulado, outbox y resultado de compra. |
| Tickets | Emisión idempotente, consulta del propietario y uso único por STAFF. |
| Lambda de compras | Consume SQS, solicita reserva, emite tickets y comunica el resultado. |
| Lambda autorizadora | Consulta Auth por red privada y aplica roles por ruta; nunca sustituye los controles de propiedad del backend. |

Los cuatro microservicios y las dos Lambdas tienen Dockerfile. ECR almacena imágenes y ECS/Fargate ejecuta los microservicios. Lambda ejecuta imágenes compatibles con su runtime. CloudFormation administra red, datos y monitoreo; los scripts coordinan comandos Docker y AWS.

### Tres entornos diferentes

| Entorno | Ejecución y datos | Qué demuestra |
| --- | --- | --- |
| Desarrollo / Floci | Docker Compose, PostgreSQL, MongoDB/Redis; Floci simula Gateway, SQS y Lambda. DynamoDB Local tiene una prueba separada. | Desarrollo sin credenciales ni consumo AWS y pruebas funcionales. |
| Práctica Swarm | Manager y worker en dos motores Docker aislados dentro de este PC; PostgreSQL, MongoDB, Redis y Floci. | Stack, red overlay, servicios distribuidos y escalado manual. Compra de referencia síncrona; no confundir con la Lambda de AWS. |
| AWS Academy | Gateway, VPC Link, ALB interno, cuatro servicios ECS, Aurora, DynamoDB, Redis, SQS, Lambdas y CloudWatch. | Integración real en nube y despliegue desde GitHub Actions. |

ECS y Swarm son destinos distintos: ECS no reemplaza la evidencia Swarm de la pauta. Dos motores en un PC no ofrecen independencia física ni toleran la caída del PC.

## 2. Arranque y pruebas desde una interfaz

Necesitas Docker Desktop iniciado. Para compilar/pruebas Java fuera de Docker usa JDK 21; para las herramientas `.mjs`, Node.js 22.

```powershell
# Solo la primera vez; no reemplaces un .env que ya tenga tus valores.
if (-not (Test-Path .env)) { Copy-Item .env.example .env }

# Compra asíncrona, Gateway emulado y Swagger común:
./infra/purchase-lambda/start-local-purchase.ps1 -Build
```

Abre **http://127.0.0.1:8090/**. Los microservicios y las bases quedan privados en Docker. Floci se administra en http://127.0.0.1:4567/, pero esa página no es la interfaz de EventPass. Después de reiniciar el PC repite el script; usa `-Build` cuando cambie Java. Recrear el contenedor Floci borra sus recursos emulados.

Para la demo anterior sin procesador asíncrono: `./infra/gateway/start-local-demo.ps1 -Build -SmokeTest`. Para desarrollo con los cuatro puertos directos: `docker compose up -d --build --wait`; Swagger individual en `http://127.0.0.1:8081/swagger-ui/index.html` hasta el puerto 8084. Termina las compras antes de cambiar entre modos.

### Compra manual en Swagger o Postman

1. Inicia sesión con ADMIN. Crea un recinto y un evento con fechas futuras; copia los UUID devueltos y publica el evento. ADMIN también puede crear ORGANIZER y STAFF.
2. Registra un CUSTOMER y haz login. En **Authorize** usa su `access_token`; `refresh_token` solo sirve para renovar.
3. `GET /api/v1/events?page=0&size=5`: copia un evento publicado y el ID de su categoría de tickets. No uses los UUID ficticios de los ejemplos como datos existentes.
4. `POST /api/v1/orders`: crea la orden del cliente. `POST /api/v1/orders/{id}/payment`: simula el pago.
5. Consulta `GET /api/v1/orders/{id}` hasta el estado final y `GET /api/v1/tickets/me` para las entradas.
6. Con STAFF, ejecuta `PATCH /api/v1/tickets/{ticketCode}/use`: el primer uso es 200; repetir debe devolver 409. CUSTOMER no puede validar su propia entrada.

La orden asíncrona pasa de `AWAITING_PAYMENT` a `PENDING_ISSUANCE`, y termina en `PAID` con ticket. Si el aforo se agotó, termina en `REJECTED_NO_CAPACITY` con pago `REFUNDED`. **No hay pasarela bancaria ni devolución de dinero real.** Un fallo técnico mantiene el trabajo pendiente; no debe tratarse como rechazo por aforo.

### Roles y seguridad

| Operación externa | Acceso |
| --- | --- |
| Registro, login, refresh, catálogo publicado y recintos | Público; refresh verifica el token de renovación. |
| Perfil y logout | Usuario humano con sesión vigente. |
| Crear/modificar/publicar/cancelar eventos | ADMIN u ORGANIZER; el organizador solo administra sus propios eventos. |
| Crear recintos | ADMIN u ORGANIZER. |
| Crear ORGANIZER/STAFF | ADMIN; el registro público solo crea CUSTOMER. |
| Órdenes y tickets | CUSTOMER, solo los propios. |
| Validación del ticket | STAFF. ADMIN/ORGANIZER no heredan este permiso. |
| Emisión, reservas y tokens de servicio | Solo rutas internas, ausentes del Gateway público. |

En AWS se usa un **Lambda REQUEST authorizer Java**, no el autorizador JWT RSA incorporado de AWS: nuestro emisor utiliza HMAC. Auth verifica firma, vencimiento, propósito ACCESS, cuenta y sesión; el Gateway aplica el rol actual por ruta sin caché (TTL 0). Refresh, SERVICE, firmas alteradas, sesiones cerradas y rutas desconocidas no autorizan acceso. Falta de Bearer puede dar 401; denegación del autorizador, 403.

Cada microservicio mantiene su validación y la propiedad de recursos. Cambios de rol/estado, eliminación y cambios de correo revocan sesiones mediante un índice por usuario en Redis. STAFF es global: todavía no existe asignación de validadores a eventos concretos. No muestres claves, Bearers, join tokens ni contraseñas en el video.

## 3. Configuración y archivos de infraestructura

| Archivo / variable | Uso |
| --- | --- |
| `.env.example` / `.env` | Valores ficticios compartidos de desarrollo / copia local ignorada por Git. Swarm tiene su `.env` aislado para no compartir credenciales de esa práctica. |
| `application.yaml`, `application-dev.yaml`, `application-prod.yaml` | Configuración Spring y perfiles; `prod` ejecuta Flyway y valida el esquema. Tickets añade `dynamodb` en AWS. |
| `JWT_SECRET`, `ADMIN_PASSWORD`, `MS_ORDERS_CLIENT_SECRET`, `REDIS_PASSWORD`, `*_DB_PASSWORD` | Secretos. En AWS se entregan por referencias ARN de Secrets Manager, nunca dentro de la imagen o del repositorio. |
| `AUTH_SERVICE_URL`, `EVENTS_SERVICE_URL`, `ORDERS_SERVICE_URL`, `TICKETS_SERVICE_URL` | Direcciones internas de dependencias; no son las URL públicas del comprador. |
| `AWS_REGION`, variables `*_QUEUE`, `TICKETS_DYNAMODB_TABLE` | Región, nombres de colas y tabla del entorno. Credenciales de prueba solo en emulación; en AWS se usa el rol de ejecución/tarea. |
| `.local/` | Inventario, referencias ARN, informes y credenciales cifradas del PC. Ignorado; no compartirlo como documentación. |
| `.ps1` | Automatización PowerShell: Docker/AWS CLI, esperas y validaciones. No reemplaza Spring Boot. |
| `.mjs` / `.test.mjs` | JavaScript Node: genera plantillas/coordina operaciones / comprueba esa lógica. No es otro backend web. |
| `infra/aws/data-bootstrap` | Contenedor temporal dentro de la VPC que prepara usuarios/bases y verifica SQL/Redis. Flyway crea las tablas de negocio. |
| `.github/workflows/tests.yml` y `deploy-aws.yml` | Pipeline de pruebas y publicación de la versión aprobada. |

No es correcto centralizar secretos AWS en el `.env` público. `.env` resuelve el desarrollo; Secrets Manager y las identidades temporales resuelven el despliegue. La pauta IE5 admite scripts, comandos e infraestructura como código; las guías del curso incluyen scripts y CloudFormation.

## 4. Swarm, escalado y prueba de carga

```powershell
./infra/swarm/test-swarm.ps1 -KeepRunning
# Con imágenes ya construidas puedes añadir -SkipBuild.
node infra/swarm/test-scaling.mjs
```

El primer comando construye cuatro imágenes, crea los dos motores, une el worker, carga imágenes, despliega `infra/swarm/docker-stack.yml`, comprueba sesión y escala Eventos 1 -> 2 -> 1. Recrea los contenedores de nodos conservando sus volúmenes para evitar PID obsoletos al reiniciar Docker-in-Docker.

El segundo genera peticiones independientes de catálogo con concurrencias 2, 4 y 8, mantiene tráfico durante subida y bajada, exige respuestas válidas sin errores y registra solicitudes, estados HTTP, p95 y máximo de latencia por fase en `.local/swarm/scaling-load.json`. Cada petición abre una conexión, sin reintentos. No mide capacidad máxima, conexiones persistentes durante la retirada, miles de compradores ni garantiza mayor velocidad con dos réplicas en el mismo PC. La prueba inicial con conexiones persistentes observó dos interrupciones TCP al reducir réplicas: no presentar esta práctica como drenaje perfecto de conexiones. El stack permite 45 s de parada y Eventos dispone de 30 s para apagado ordenado. Siempre se intenta restaurar una réplica.

```powershell
$swarm = @('compose','-p','eventpass-swarm-lab','-f','infra/swarm/compose.lab.yaml')
docker @swarm exec manager docker node ls
docker @swarm exec manager docker stack services eventpass
docker @swarm exec manager docker service ps eventpass_ms-events
docker @swarm exec manager docker service scale eventpass_ms-events=2
Invoke-RestMethod 'http://127.0.0.1:18082/api/v1/events?page=0&size=1'
docker @swarm exec manager docker service scale eventpass_ms-events=1
docker @swarm down
```

`docker-stack.yml` usa imágenes construidas, `deploy`, red overlay, routing mesh y actualización start-first con rollback. Swarm no usa `build` ni `depends_on`. `env_file` carga explícitamente su configuración. Datos fijados al manager etiquetado; aplicaciones en el worker. No escales bases de datos con `service scale`: sus volúmenes locales no se replican. Swarm usa Docker-in-Docker privilegiado exclusivamente para esta práctica, sin montar el socket del PC ni publicar la API del motor.

Para dos máquinas Linux: `docker swarm init --advertise-addr <IP_PRIVADA_MANAGER>`, etiqueta el nodo con `docker node update --label-add eventpass-data=true <NODE_ID>` y obtén `docker swarm join-token worker`. Ejecuta el join en el segundo host. Entre nodos permite 2377/TCP, 7946/TCP+UDP y 4789/UDP; no desde Internet. Carga las mismas imágenes en ambos o usa ECR autenticado. Desde la carpeta del stack: `docker stack deploy --with-registry-auth -c docker-stack.yml eventpass`. Un manager y un worker no constituyen alta disponibilidad.

## 5. AWS Academy: conexión y despliegue

La sala utilizada es la cuenta `119065066402`, región `us-east-1`. Entra por AWS Academy -> Start Lab -> indicador verde -> botón AWS; no necesitas contraseña del ID de cuenta.

```powershell
# AWS Details -> AWS CLI: introduce los tres valores actuales en entradas ocultas.
./infra/aws/connect-learner-lab.ps1
# Actualiza también los Secrets temporales de GitHub y habilita despliegue:
./infra/aws/configure-github-lab.ps1 -EnableDeployment
```

Se necesita Docker Desktop listo. El asistente usa la imagen oficial AWS CLI, cifra la sesión con el usuario de Windows y comprueba identidad y acceso real a ECS. STS por sí solo no demuestra permisos. Si aparece `voc-cancel-cred`, inicia el laboratorio y renueva los tres valores actuales; no cambies políticas IAM para saltar esa restricción. Renovar el PC no renueva GitHub: ejecuta ambos comandos. Sin `-EnableDeployment`, el segundo deja la publicación deshabilitada.

### Recursos y límites

- VPC propia, dos zonas, subredes públicas, privadas de aplicación y privadas de datos. ECS sin IP pública; ALB interno; entrada por VPC Link. NAT único para salida de aplicación; datos sin ruta por defecto a Internet. Endpoints S3/DynamoDB.
- Aurora PostgreSQL 17.11: tres bases y usuarios separados en un clúster/instancia compartidos; acceso cruzado rechazado. TLS con validación de certificado. Redis 7.1 con contraseña, TLS y cifrado en reposo. Tickets en DynamoDB con escritura transaccional y uso condicional único.
- Cuatro servicios ECS Fargate, 512 CPU/1 GiB por tarea, imágenes por digest, health checks, actualización progresiva y circuit breaker de despliegue con rollback.
- Cinco colas SQS; compras con visibilidad 120 s, cinco entregas y DLQ retenida 14 días. Lambda de compras de imagen; vínculo BatchSize 1. Imagen Java separada para el autorizador.
- CloudWatch: logs retenidos siete días, panel `eventpass-lab`, nueve alarmas operativas. Sin avisos SNS ni acciones externas. Las alarmas de target tracking pueden añadirse aparte.
- Eventos: target tracking CPU 60%, mínimo 1/máximo 2 tareas, espera de subida 60 s y bajada 180 s. `start-lab-scaling.ps1` configura y verifica la política. Una política existente no acredita que ya se observó una transición automática bajo carga.
- `LabRole` compartido es una restricción del laboratorio; los roles de EventPass no son roles IAM. El autoescalador usa el rol vinculado al servicio administrado por AWS. No se modifican permisos de `LabRole`.
- No hay alta disponibilidad completa: un NAT, un writer SQL y un Redis. No hay dominio propio ni pasarela de pago real. Cloud Map no fue autorizado; las llamadas internas usan listeners privados del ALB.

### Aprovisionamiento y verificaciones

El laboratorio actual ya está desplegado. No vuelvas a crearlo para cada commit. Orden de preparación inicial, cuando los recursos todavía no existen:

```powershell
./infra/aws/initialize-lab.ps1
./infra/aws/start-lab-network.ps1 -Apply
./infra/aws/start-lab-data.ps1 -Apply
./infra/aws/initialize-lab-data.ps1
./infra/aws/start-lab-services.ps1
./infra/aws/start-lab-purchase.ps1
./infra/aws/start-lab-authorizer.ps1
./infra/aws/start-lab-network.ps1 -Apply
./infra/aws/start-lab-monitoring.ps1 -Apply
./infra/aws/start-lab-scaling.ps1
```

Los comandos de red/datos/monitoreo sin `-Apply` preparan un change set; rechazan eliminaciones y reemplazos. La inicialización SQL comprueba TLS, aislamiento de usuarios y Redis sin conservar datos de prueba. Las publicaciones manuales exigen un commit identificado y no deben superponerse al pipeline. El pipeline reconcilia una sala preparada: no es un instalador completo desde una cuenta vacía.

```powershell
./infra/aws/Sync-LabServiceState.ps1
./infra/aws/test-lab-network.ps1 -ServicesReady
./infra/aws/test-lab-services.ps1
./infra/aws/test-lab-purchase.ps1
./infra/aws/test-lab-security.ps1
./infra/aws/start-lab-scaling.ps1 -VerifyOnly
```

`Sync` solo actualiza informes locales. Las demás pruebas validan red, sesiones, roles, propiedad, compra, aforo y tickets; crean datos identificables de prueba. No hacen cobros reales. El Gateway público se obtiene del output `GatewayUrl` de `eventpass-network`; en Postman usa esa base con las mismas rutas públicas de Swagger. El panel Swagger local apunta a Floci, no a AWS. No presentes sus respuestas como evidencia de nube.

### CI/CD y trazabilidad

Push a `develop` dispara `.github/workflows/tests.yml`: pruebas de microservicios afectados, plataforma E2E, Gateway privado Floci, compra asíncrona y DLQ, perfiles de producción/Flyway, DynamoDB y Swarm con carga. Una falla bloquea publicación. Después, si `AWS_DEPLOY_ENABLED=true` y la sesión está vigente, se ejecuta `deploy-aws.yml` en `aws-lab`.

El despliegue verifica cuenta/recursos/commit, reconcilia colas/red/datos, ejecuta bootstrap, construye y publica imágenes con commit y digest, actualiza ECS y Lambda, publica el autorizador Java, conecta las rutas y configura monitoreo/autoescalado. Finalmente ejecuta pruebas reales de sesiones, roles, compras, tickets y aislamiento JWT. Confirma que ECS conserva la nueva revisión: un rollback a la anterior falla la comprobación.

GitHub usa Secrets temporales `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` y variables `AWS_TASK_CONFIG`, `AWS_DEPLOY_CONFIG`, `AWS_DEPLOY_ENABLED`. Los JSON contienen referencias ARN, no contraseñas. Un fallo de pruebas posteriores marca el job fallido; no revierte automáticamente todos los componentes como una transacción. Cambios de esquema y contratos deben permitir convivencia entre versiones.

## 6. Resiliencia y recuperación de compras

Órdenes protege sus llamadas REST a Auth y Eventos con circuitos independientes Resilience4j. Ventana 10, mínimo 5 llamadas, apertura al 50% de fallos, espera 15 s y dos sondas de recuperación. Conexión limitada a 2 s y lectura a 4 s. Red, HTTP 5xx y 429 cuentan como fallos; 400/401/403/404/409 no indican caída y no abren el circuito. Circuito abierto o dependencia caída devuelve 503 sin detalles internos ni éxito ficticio. No hay reintento HTTP automático de pagos o reservas.

Lambda protege por separado Auth, Eventos, Tickets y Órdenes con ventana 10/mínimo 5/50%, espera 15 s y una sonda. Su estado es local al entorno Lambda caliente: no es un cortacircuitos distribuido. SQS controla los reintentos; se conservan las claves idempotentes de reserva, emisión y resultado. El autorizador siempre deniega si Auth/Redis fallan. Auth, Eventos y Tickets no necesitan circuitos HTTP salientes si no llaman a otro microservicio por HTTP. Los CB del diagrama deben representar estas dependencias; el circuit breaker de despliegue ECS es otro mecanismo.

La DLQ permite recuperar un fallo persistente después de corregir la causa. Para prueba controlada sin otras compras activas: `./infra/aws/test-lab-purchase-dlq.ps1`. Inyecta un fallo restringido a una orden y restaura la imagen estable; no lo ejecutes durante compras ajenas ni un despliegue concurrente. Para una orden fallida concreta:

```powershell
./infra/aws/recover-lab-purchase.ps1 -OrderId '<UUID_DE_LA_ORDEN>'
```

Comprueba luego orden, aforo y tickets. No publiques de nuevo todas las compras ni borres la cola. La alarma DLQ se prueba con `./infra/aws/test-lab-monitoring.ps1`: introduce una sonda técnica, comprueba OK -> ALARM -> OK y la retira. Puede tardar varios minutos; es diferente de una compra real.

En Floci, `./infra/purchase-lambda/recover-local-purchases.ps1 -Confirm` devuelve toda la DLQ local. Para acelerar la prueba aislada: arranca con `-VisibilityTimeout 25 -MaxReceiveCount 2`, activa `E2E_INTERRUPT_TICKETS=true`, `E2E_PURCHASE_DLQ=true` y ejecuta la prueba de compra. Restaura después los valores habituales.

## 7. Pruebas repetibles

```powershell
# Unitarias/integración de cada servicio (Testcontainers requiere Docker):
./ms-orders/mvnw.cmd -f ms-orders/pom.xml test
# Pruebas de Lambda y automatización:
node --test infra/purchase-lambda/*.test.mjs
node --test infra/aws/*.test.mjs
# E2E del entorno levantado; usa credenciales de prueba de ese entorno:
$env:AUTH_BASE_URL = 'http://127.0.0.1:18081'
$env:EVENTS_BASE_URL = 'http://127.0.0.1:18082'
$env:ORDERS_BASE_URL = 'http://127.0.0.1:18083'
$env:TICKETS_BASE_URL = 'http://127.0.0.1:18084'
./ms-auth/mvnw.cmd -f e2e-tests/pom.xml verify
```

Para Floci los cuatro `*_BASE_URL` valen `http://127.0.0.1:8090`, `E2E_ASYNC_PURCHASE=true`; ejecuta `'-Dit.test=*,!PlatformHealthIT,!OrderItemCapacityIT'`. Health y operaciones internas se comprueban por acceso directo, no deben publicarse en Gateway. Las E2E usan `E2E_ADMIN_EMAIL`, `E2E_ADMIN_PASSWORD` y, cuando se prueban rutas internas, `MS_ORDERS_CLIENT_ID`/`MS_ORDERS_CLIENT_SECRET` del entorno. Usa una terminal separada para no mezclar variables entre modos.

Los perfiles `prod` se prueban desde bases vacías con `infra/database/compose.verify.yaml`; para Tickets añade `compose.dynamodb.yaml` e inicializa la tabla con `tickets-table.json`. El pipeline contiene los comandos completos y verifica idempotencia y doble uso concurrente. No actives Flyway sobre una base histórica sin revisar un baseline: `dev` y `prod` no son migraciones automáticas entre sí.

Informes en `.local/`: red, datos, servicios, compras, DLQ, monitoreo, JWT y escalado; Swarm en `.local/swarm/`. Son evidencia técnica local, no sustituyen una ejecución actual ni la presentación. GitHub conserva logs y el artefacto Swarm con los resultados de carga.

## 8. Pauta EP3 y video

| Indicadores | Evidencia a mostrar |
| --- | --- |
| IE1 (10%), IE2 (5%) | Dockerfile multietapa, usuario no root, build/arranque local y endpoints de los cuatro servicios. |
| IE3 (5%), IE7 (10%) | `docker-stack.yml`, manager/worker Ready, stack desplegado y servicios distribuidos. |
| IE4 (10%), IE5 (10%), IE6 (5%) | Commit/push -> build/test -> configuración cloud -> publicación -> microservicio accesible por Gateway. |
| IE8 (5%), IE9 (5%) | Escalado Swarm 1 -> 2 -> 1 con tráfico, resultados y explicación de overlay, datos, disponibilidad y mantenimiento. |
| IE10 (10%), IE13 (5%) | Código/Dockerfile de Lambda, disparador SQS y ejecución conectada a servicios. |
| IE11 (6%), IE14 (2%) | Creación/configuración de la cola cloud, productor Órdenes y consumidor Lambda con una compra identificable. |
| IE12 (4%) | Gateway, VPC Link, ALB privado, JWT/roles, denegación y ausencia de rutas internas. |
| IE15 (3%) | Compra completa, ticket, aforo agotado/devolución simulada y relación con prueba de escalabilidad. |
| IE16 (3%), IE17 (2%) | Video ordenado, lenguaje técnico, voces de ambos estudiantes y audio claro. |

La pauta pide **video de 3 a 8 minutos**, MP4 o similar, realizado en pareja; no exige mostrar el rostro. Entrega los enlaces al código y el video en AVA y copia al correo del docente según su plazo. No envíes correo automáticamente desde herramientas de desarrollo. Las guías incluyen alternativas: no se exige agregar simultáneamente Kubernetes, CodePipeline, ELK y Prometheus para repetir lo que ya resuelven las herramientas elegidas.

Guion sugerido de 7:30, repartiendo narración y demostraciones entre ambos:

| Tiempo | Pantalla y acción |
| --- | --- |
| 0:00–0:35 | Presentar EventPass y recorrido de la arquitectura. |
| 0:35–1:30 | Dockerfile, build, contenedores y endpoints locales. |
| 1:30–2:30 | Swarm: nodos, stack, carga y cambio de réplicas. |
| 2:30–3:30 | Commit/push y pipeline; mostrar el mismo SHA en el despliegue y endpoint AWS funcional. |
| 3:30–4:20 | Gateway privado: petición permitida, cliente rechazado y ruta interna 404. |
| 4:20–6:30 | SQS configurada, compra pendiente, Lambda, ticket, uso STAFF y segunda compra sin aforo. |
| 6:30–7:30 | CloudWatch, resiliencia, límites del laboratorio y cierre. |

Graba las esperas largas y edítalas conservando continuidad del mismo commit/orden. No leas JSON completo: señala estado, ID, rol, código HTTP y resultado de negocio. Antes de grabar, renueva credenciales, comprueba salud/colas, prepara eventos futuros y cuentas de prueba y realiza un ensayo completo. La configuración de autoescalado CPU se muestra como configuración hasta que su historial registre una transición real por carga; no confundirla con el escalado manual demostrado en Swarm.

Referencias de implementación: [circuit breaker Resilience4j](https://resilience4j.readme.io/docs/circuitbreaker), [escalado de servicios ECS](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-auto-scaling.html). Las instrucciones de entrega corresponden a la pauta EP3 JVY0101 compartida por el docente; no se incorporan sus documentos privados al repositorio.
