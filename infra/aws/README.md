# Preparación de EventPass para ECS

Auth, Eventos, Órdenes y Tickets siguen siendo aplicaciones Spring Boot. Maven compila cada JAR, Docker lo incorpora a una imagen, ECR almacena esa imagen y ECS ejecuta el contenedor. Este directorio prepara las definiciones de tarea. La red, los servicios ECS y las integraciones requieren los identificadores y permisos de la sala.

## Preparar las imágenes y tareas

El despliegue de los cuatro microservicios usa sus Dockerfiles multietapa: Maven compila dentro de Docker y la etapa final incorpora Java 21 JRE y el JAR. Los contenedores ejecutan el proceso como `spring`, UID/GID 10001. Cada contexto tiene un `.dockerignore` que excluye compilados locales, archivos de entorno, credenciales y archivos del editor. Los secretos se entregan al arrancar el contenedor.

Docker construye y publica las imágenes; ECR las almacena y ECS/Fargate ejecuta los cuatro microservicios. La guía del curso presenta ECS/Fargate como alternativa a EC2 con Docker Swarm, y el diagrama autorizado utiliza ECS. El procesador Lambda también se construye con un Dockerfile y usa su propia imagen en ECR. GitHub Actions comprueba el usuario efectivo de los cuatro servicios, prueba la imagen de Lambda con archivos de solo lectura y ejecuta las pruebas funcionales con DynamoDB.

1. Construye las cuatro imágenes con sus Dockerfile para Linux x86-64. Sube cada imagen a un repositorio ECR autorizado y anota su digest `sha256`. Usa ese digest para que cada tarea arranque con una versión concreta.
2. Completa una copia de `task-config.example.json` con los ARNs de roles, referencias a secretos, grupos de CloudWatch Logs, imágenes ECR y direcciones privadas. El archivo de ejemplo usa dominios y ARNs ficticios. El rol de ejecución se usa al descargar imágenes, preparar logs y obtener secretos de arranque. El rol de tarea autoriza las llamadas de la aplicación, como SQS para Órdenes y DynamoDB para Tickets.
3. Genera los cuatro JSON de ECS con Node.js. El comando solo escribe archivos.

```powershell
node infra/aws/render-task-definitions.mjs ruta-a-tu-config.json "$env:TEMP/eventpass-task-definitions"
```

Cada tarea usa Fargate, red `awsvpc`, 512 unidades de CPU y 1 GiB de memoria como punto de partida. Registra las definiciones cuando la sala confirme Fargate y los roles disponibles. Si exige ECS sobre EC2, adapta la definición y la capacidad del clúster antes de registrarla. Auth, Eventos y Órdenes usan el perfil `prod` con Flyway. Tickets usa `prod,dynamodb`.

## Conectar la infraestructura

1. Prepara bases Aurora PostgreSQL, DynamoDB, Redis privado, colas SQS y la cola de errores. Crea también las colas de publicación y cancelación de Eventos y la cola de compatibilidad que escucha Tickets. Usa las referencias de entorno incluidas en la configuración.
2. Ejecuta un servicio ECS por microservicio en subredes privadas con IP pública deshabilitada. Configura descubrimiento privado para las URL de servicio del ejemplo, o reemplázalas por las URL privadas que admita la sala. Lambda necesita acceso privado a Auth, Eventos, Órdenes y Tickets.
3. Construye `infra/purchase-lambda/Dockerfile` y publica su imagen en `eventpass/purchase-lambda` con `Publish-LabPurchaseImage.ps1`. Configura Lambda con `PackageType=Image` y la URI ECR fijada por digest. Sus variables contienen las URL privadas y la referencia ARN de la credencial interna. Vincula la cola de compras mediante un event source mapping y valida acceso a VPC, SQS y CloudWatch.
4. Configura cuatro target groups de tipo `ip` con health check en `/actuator/health` y un ALB interno en dos zonas. Conecta el listener con un VPC Link V2 desde una HTTP API. Crea los métodos concretos que aparecen en `infra/gateway/routes.json`. Mantén las rutas internas fuera del Gateway y conserva el path original en la integración.
5. Comprueba el catálogo y el login desde la URL del Gateway. Después simula un pago, verifica la emisión o devolución, y confirma que las rutas de servicio y aforo quedan fuera del acceso público.

El diagrama pide validación JWT en el Gateway. Actualmente la firma es HMAC y Spring valida el token y la sesión en Redis. Si el docente requiere esa validación antes de enrutar, queda por decidir cómo integrarla en AWS.

## Verificación previa a la sala

GitHub Actions prueba el flujo de referencia, el Gateway privado con Floci y la compra asíncrona con recuperación desde la cola de errores. Un job separado comprueba Flyway en bases nuevas, la emisión y el uso de entradas con DynamoDB Local, las pruebas del código de Lambda y la generación de las cuatro definiciones ECS.

Antes de aprovisionar recursos, confirma la cuenta y región desde la consola de la sala. La terminal del navegador y la consola pueden usar identidades distintas. Comprueba qué identidad tiene autorización para subir imágenes a ECR y si el laboratorio permite registrar tareas Fargate con el rol `LabRole`. Confirma también las subredes privadas, los permisos de VPC Link y la salida necesaria para que ECS descargue imágenes y se conecte a SQS, DynamoDB, Secrets Manager y CloudWatch.

La primera validación en AWS consiste en publicar una imagen y arrancar una tarea conectada a sus dependencias. Después se conectan las cuatro tareas al ALB privado y se repite la compra completa por API Gateway. El alcance de esa prueba incluye sesión, emisión, rechazo por aforo y recuperación desde la cola de errores.

## Pipeline de pruebas y publicación

### Conectar el computador a la sala

La cuenta de laboratorio confirmada es `119065066402`, en `us-east-1`. Para entrar a la consola usa AWS Academy → Start Lab → botón AWS. No necesitas una contraseña del ID de cuenta. En AWS Details → AWS CLI aparecen las credenciales temporales `aws_access_key_id`, `aws_secret_access_key` y `aws_session_token`.

Con Docker Desktop abierto, ejecuta en PowerShell 7:

```powershell
./infra/aws/connect-learner-lab.ps1
```

Introduce solo el valor de cada campo en las tres entradas ocultas. El asistente usa la imagen oficial de AWS CLI en Docker; guarda las credenciales cifradas con el usuario de Windows en `.local/aws-lab-session.xml`, ignorado por Git, y verifica la identidad. No publiques ese archivo ni copies las claves al chat. Al caducar la sesión, repite el asistente.

Después puedes consultar AWS sin instalar la CLI en Windows:

```powershell
./infra/aws/Invoke-LabAws.ps1 -AwsArguments @('sts', 'get-caller-identity', '--output', 'json')
./infra/aws/initialize-lab.ps1 -WhatIf
./infra/aws/initialize-lab.ps1
```

La inicialización reutiliza los repositorios existentes y completa ECR, los logs de ECS con retención de siete días, cinco colas SQS, la tabla DynamoDB de Tickets y el clúster Fargate vacío. Configura cinco intentos, visibilidad de 120 segundos y retención de errores de catorce días. Guarda un inventario local sin claves. No inicia tareas, bases SQL, Redis, NAT ni balanceadores. Esos recursos y la red se preparan después de verificar permisos y conectividad de la sala. Requiere el rol existente `LabRole`; no crea roles IAM.

### Red privada de la sala

```powershell
./infra/aws/start-lab-network.ps1
./infra/aws/start-lab-network.ps1 -Apply
```

El primer comando genera y valida la plantilla y prepara un change set de CloudFormation. El segundo prepara y ejecuta el plan; rechaza eliminaciones o reemplazos. El stack `eventpass-network` reúne una VPC propia `10.42.0.0/16`, dos subredes públicas, dos privadas de aplicación y dos privadas de datos en dos zonas. Las de aplicación tienen salida por un NAT; las de datos no tienen ruta por defecto a Internet. Ninguna subred asigna IP pública automáticamente. S3 y DynamoDB tienen gateway endpoints.

API Gateway tiene únicamente las rutas de `infra/gateway/routes.json`. La integración conserva el path y accede por VPC Link al listener 80 de un ALB interno. Cada servicio tiene su target group de tipo `ip` y health check `/actuator/health`. Los listeners internos 8081–8084 permiten a ECS y Lambda llamar al servicio correspondiente mediante las URL de los outputs `AuthServiceUrl`, `EventsServiceUrl`, `OrdersServiceUrl` y `TicketsServiceUrl`. Sus grupos de seguridad aceptan esos puertos solo desde las aplicaciones y Lambda; el VPC Link accede únicamente al puerto 80.

Esta cuenta permite listar Cloud Map, pero denegó `servicediscovery:CreatePrivateDnsNamespace`. Por eso se usan las URL privadas estables del ALB para las variables `*_SERVICE_URL`. No se crean espacios Cloud Map ni roles IAM. Las aplicaciones siguen validando los tokens y sesiones; el authorizer del Gateway continúa pendiente.

El NAT y el ALB generan cargos mientras existan. La red de laboratorio usa un solo NAT y no tiene tolerancia a fallos de NAT entre zonas. Los outputs reales se guardan en `.local/aws-network-outputs.json`. Crear esta red no despliega las aplicaciones: faltan bases SQL, Redis, secretos, imágenes, tareas, Lambda y su vínculo SQS. Las pruebas funcionales completas por esta URL se ejecutan cuando existan targets sanos.

```powershell
./infra/aws/test-lab-network.ps1
# Después de desplegar las tareas y sus dependencias:
./infra/aws/test-lab-network.ps1 -ServicesReady
```

La primera prueba comprueba los recursos reales: VPC Link disponible, ALB interno, rutas públicas exactas, subredes privadas, salida de aplicación por NAT y ausencia de ingreso abierto en grupos de seguridad. También comprueba que las rutas internas y Actuator devuelven 404 por el Gateway. Antes de arrancar ECS, el catálogo devuelve 503 por falta de targets; eso comprueba el recorrido hasta el balanceador, no el funcionamiento del catálogo. Con `-ServicesReady` se exige HTTP 200.

### Aurora, Redis y secretos

```powershell
./infra/aws/start-lab-data.ps1
./infra/aws/start-lab-data.ps1 -Apply
./infra/aws/initialize-lab-data.ps1
```

El stack `eventpass-data` crea Aurora PostgreSQL 17.11 con una instancia `db.t4g.medium`, un Redis OSS 7.1 `cache.t3.micro` y siete secretos de aplicación generados por AWS. RDS administra además el secreto del usuario maestro. SQL y Redis se ubican en las subredes privadas de datos. SQL exige TLS; Redis tiene TLS obligatorio, contraseña y cifrado en reposo. Ambos generan cargos mientras estén activos.

Las bases `eventpass_auth`, `eventpass_events` y `eventpass_orders` comparten el clúster Aurora para reducir consumo en la sala. Cada una tiene propietario y contraseña propios; sus usuarios no pueden conectarse a las otras dos. Comparten capacidad y disponibilidad física: para aislar esos aspectos harían falta clústeres separados. El laboratorio usa un writer SQL y un nodo Redis, sin alta disponibilidad de instancias.

`initialize-lab-data.ps1` construye y publica una imagen auxiliar en `eventpass/data-bootstrap` y la ejecuta una vez en Fargate, sin IP pública. La tarea recibe las contraseñas mediante referencias de Secrets Manager e inyección de secretos de ECS. Crea los usuarios y bases, comprueba el propietario al reutilizarlas y conserva las contraseñas existentes. PostgreSQL 17 necesita que el usuario maestro pueda asumir el rol propietario para crear la base; ese permiso se concede al maestro, sin conceder privilegios de administración a los usuarios de aplicación.

La tarea comprueba TLS y lectura/escritura en las tres bases, seis conexiones cruzadas que deben rechazarse, y Redis con contraseña, TTL, escritura, lectura y borrado. Rechaza el acceso a Redis sin contraseña. Sus tablas de prueba son temporales y hacen rollback; la clave de prueba de Redis se borra y también tiene TTL de sesenta segundos. Los logs no imprimen contraseñas, SQL de creación de usuarios ni errores completos del cliente. Después de la verificación, la tarea se detiene y queda un informe en `.local/aws-data-verification.json`.

Los outputs se guardan en `.local/aws-data-outputs.json`. `task-settings.mjs` genera `.local/aws-task-settings.json` con las direcciones privadas, usuarios y referencias ARN con selector `:password::`. El secreto maestro no se entrega a los microservicios. Las imágenes SQL incluyen el certificado de RDS y sus URL usan `sslmode=verify-full`. Redis se configura con `REDIS_SSL=true`.

El archivo generado aún tiene `images: {}`: los digests reales de ECR se completan en la etapa de publicación de los cuatro servicios. No lo uses para habilitar despliegue automático hasta completar esa etapa. Las tablas de negocio y `flyway_schema_history` se crean cuando Spring Boot arranca con el perfil `prod`; esta tarea solo prepara bases, roles y verifica sus conexiones.

El script de CloudFormation rechaza eliminaciones o reemplazos de recursos de datos. El clúster conserva un snapshot al eliminarse desde CloudFormation y los secretos de aplicación tienen política de conservación. Esta conservación no crea un procedimiento automático de restauración ni detiene los cargos; las rotaciones de contraseñas SQL requieren actualizar el rol y su secreto de forma coordinada.

### Desplegar y comprobar los cuatro servicios

Después de verificar la capa de datos, guarda los cambios de los microservicios en un commit y ejecuta:

```powershell
./infra/aws/start-lab-services.ps1
./infra/aws/test-lab-network.ps1 -ServicesReady
./infra/aws/test-lab-services.ps1
```

La publicación construye imágenes Linux x86-64, las sube a ECR y registra las tareas con el digest de cada imagen. Los cuatro servicios Fargate usan subredes privadas, sin IP pública, y reciben tráfico del ALB interno. Las contraseñas se inyectan desde Secrets Manager. Spring Boot ejecuta las migraciones de las bases SQL con el perfil `prod`; Tickets utiliza `prod,dynamodb`.

El script exige que ECS complete el despliegue de las definiciones solicitadas, con todas las tareas previstas en ejecución y sin tareas pendientes. Una versión anterior estable no cuenta como éxito de la versión nueva. Si se interrumpe la espera, puede repetirse sin construir imágenes:

```powershell
./infra/aws/Wait-LabServices.ps1
# Para actualizar únicamente Tickets después del primer despliegue:
./infra/aws/start-lab-services.ps1 -Services ms-tickets
```

La espera usa las definiciones solicitadas guardadas en `.local/*-service.json`. La actualización parcial conserva los digests de los otros servicios en `.local/aws-task-config.json`. Si faltan esos datos, el primer despliegue debe incluir los cuatro servicios.

`test-lab-services.ps1` comprueba el catálogo por API Gateway, las rutas internas bloqueadas, registro, login, renovación y revocación de sesión, acceso autenticado a Órdenes y Tickets, y rechazo de acciones administrativas para compradores. Crea un evento de prueba y una orden, comprueba el importe, cancela la orden y verifica que el aforo no cambió. Cancela el evento y cierra las sesiones al terminar. Conserva registros de prueba con nombres únicos; no elimina datos de negocio existentes. Los informes quedan en `.local/`, sin contraseñas ni tokens.

Esta prueba no realiza el pago simulado. Usa las pruebas de compra y de recuperación de las secciones siguientes para comprobar emisión, aforo lleno, devolución simulada y fallos persistentes. Mantén deshabilitado el despliegue automático del laboratorio hasta completar esas verificaciones y preparar su configuración y credenciales.

### Compra con SQS y Lambda en la sala

Con los cuatro servicios estables y la sesión del laboratorio vigente:

```powershell
./infra/aws/start-lab-purchase.ps1
./infra/aws/test-lab-purchase.ps1
```

El primer script construye y publica el Dockerfile del procesador en `eventpass/purchase-lambda` y crea o actualiza `eventpass-process-purchase-image`. Usa la base oficial de Lambda Node.js 22, el SDK de Secrets Manager fijado con `package-lock.json`, UID/GID 10001, timeout de veinte segundos y `LabRole`. Compila para Linux x86-64 con `--provenance=false`, como exige el formato de imagen de Lambda, y despliega el digest de ECR. La función se conecta a las subredes privadas de aplicación y llama al balanceador interno; obtiene la credencial desde Secrets Manager, sin incluir su valor en la imagen.

AWS no permite cambiar una función existente entre ZIP e imagen. La migración crea la función de imagen y verifica una invocación sin compras que comprueba acceso a Auth antes de mover el vínculo SQS existente. La función ZIP `eventpass-process-purchase` se conserva sin ese consumidor como respaldo de migración; el vínculo sigue siendo único. No se borran colas, datos ni la función anterior. [Restricción de tipos de paquete en AWS](https://docs.aws.amazon.com/lambda/latest/dg/images-create.html). El informe local guarda la función y la imagen activas, el UUID del vínculo y su función anterior.

El emulador Floci sigue usando las mismas fuentes mediante su API ZIP y ejecutándolas con su runtime Docker. La entrega real de los cuatro servicios y Lambda en AWS usa las cinco imágenes construidas por sus Dockerfiles.

El vínculo procesa un mensaje por invocación y limita la concurrencia a dos para la sala. Comprueba visibilidad mínima de 120 segundos, cinco intentos y destino `eventpass-purchase-errors`; rechaza otro consumidor Lambda para esa cola. Una excepción deja el mensaje disponible para reintentar. La cola estándar no garantiza orden estricto ni entrega única: la protección contra sobreventa y duplicación depende de las reservas y emisiones idempotentes de los microservicios.

La prueba crea dos compradores y dos órdenes antes del pago para un único cupo. Paga ambas con el proveedor simulado y espera una orden `PAID/APPROVED` con un ticket y otra `REJECTED_NO_CAPACITY/REFUNDED` sin ticket. Después invoca la función otra vez con cada compra para comprobar que conserva el resultado, el ticket y el aforo. También comprueba privacidad del ticket, acceso solo para STAFF y rechazo de reutilización con HTTP 409. No conecta una pasarela ni mueve dinero real.

El informe `.local/aws-purchase-smoke.json` contiene los estados de las comprobaciones y las órdenes de prueba, sin JWT, contraseñas ni códigos de entrada. Al terminar correctamente cancela el evento y cierra las sesiones; conserva los registros de prueba. Si falla, conserva el evento para diagnosticar la compra pendiente y cierra las sesiones. Este recorrido no fuerza un fallo persistente ni demuestra el traslado y redrive de un mensaje por la DLQ real; esa recuperación requiere una prueba separada y controlada.

### Fallo persistente y recuperación selectiva en AWS

```powershell
./infra/aws/test-lab-purchase-dlq.ps1
```

La prueba exige una Lambda estable con `PackageType=Image` y un digest de `eventpass/purchase-lambda` en la cuenta autorizada. Guarda ese digest como imagen de restauración en `.local/`, prepara una orden propia y construye otra imagen Docker que falla únicamente al emitir el ticket de esa orden. Auth, reserva de cupo y las demás órdenes conservan su comportamiento. No cambia las variables de Lambda, la política de las colas ni los servicios ECS. Las actualizaciones usan el `RevisionId` de AWS para detectar cambios concurrentes.

La orden paga con el proveedor simulado y agota cinco intentos con visibilidad de 120 segundos. La espera puede durar más de diez minutos, con un límite de veinticinco. Al encontrar el mensaje exacto en `eventpass-purchase-errors`, comprueba los fallos en CloudWatch y que la orden siga `PENDING_ISSUANCE/APPROVED`, con el cupo reservado y sin ticket. Un fallo técnico no equivale a rechazo por aforo ni confirma por sí solo una devolución.

Después restaura el código normal y verifica su hash antes de recuperar la orden. La recuperación recibe lotes de la DLQ, selecciona el `orderId`, conserva cuerpo y atributos y reenvía a `eventpass-purchase-pending`. Borra el mensaje de la DLQ solo después de la confirmación de SQS. Los mensajes ajenos recuperan visibilidad inmediatamente; su contenido no se cambia ni se borra. Si el proceso cae entre el envío y el borrado, puede haber otra entrega: los microservicios deben conservar su idempotencia. Una búsqueda limitada puede no encontrar la orden si hay muchos mensajes; informa el resultado y permite repetirla.

Para recuperar una orden concreta después de resolver su causa:

```powershell
./infra/aws/recover-lab-purchase.ps1 -OrderId 'UUID_DE_LA_ORDEN'
```

La selección usa `ReceiveMessage`, `SendMessage` y `DeleteMessage`. El redrive nativo `StartMessageMoveTask` mueve mensajes sin permitir filtrarlos por orden; por eso no se usa para esta recuperación selectiva. [Documentación de AWS sobre redrive](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue-redrive.html).

El script confirma el reenvío, no la emisión: consulta después la orden y sus tickets. La prueba completa espera `PAID/APPROVED`, exactamente un ticket y el mismo aforo reservado. Cancela su evento y cierra sesiones al terminar correctamente; conserva datos e informe sin contraseñas ni JWT en `.local/aws-dlq-smoke.json`.

El bloque `finally` intenta restaurar el código también si la prueba falla. Si se cierra el proceso o se apaga el PC, vuelve a conectar la sesión del laboratorio y ejecuta:

```powershell
./infra/aws/test-lab-purchase-dlq.ps1 -Restore
```

La restauración vuelve al digest original y rechaza una versión de Lambda ajena a la prueba para evitar sobrescribir otro despliegue. No borres imágenes de ECR ni ejecutes publicaciones de Lambda durante esta prueba. Tras una interrupción, revisa la orden; si está en la DLQ, usa la recuperación selectiva. No se purgan colas ni se reenvían todos sus mensajes de forma indiscriminada.

GitHub Actions ejecuta pruebas del fallo aislado y del recuperador con AWS simulado: cuenta incorrecta, selección del mensaje, conservación de atributos, confirmación antes del borrado y fallo de envío. La prueba real de veinticinco minutos se ejecuta manualmente en la sala y no forma parte del pipeline de cada commit.

### Monitoreo y alarmas de CloudWatch

```powershell
./infra/aws/start-lab-monitoring.ps1
./infra/aws/start-lab-monitoring.ps1 -Apply
./infra/aws/test-lab-monitoring.ps1
```

El stack `eventpass-monitoring` crea el panel `eventpass-lab` y nueve alarmas. El primer comando prepara el change set; `-Apply` lo ejecuta y rechaza eliminaciones o reemplazos. El panel se abre en AWS → CloudWatch → Dashboards → `eventpass-lab`.

| Alarma | Condición |
| --- | --- |
| `eventpass-purchase-dead-letter` | Al menos un mensaje visible o en revisión en la DLQ, en dos de tres muestras de un minuto. |
| `eventpass-purchase-queue-age` | Mensaje de la cola pendiente con antigüedad superior a cinco minutos, en dos muestras de un minuto. |
| `eventpass-lambda-errors` | Al menos un error del procesador en un minuto. |
| `eventpass-lambda-duration` | Ejecución de al menos quince segundos; timeout actual de veinte. |
| `eventpass-lambda-throttles` | Al menos una invocación limitada por concurrencia en un minuto. |
| `eventpass-auth/events/orders/tickets-unavailable` | Menos de un destino sano del servicio durante dos minutos; la ausencia de métricas también se considera incidencia. |

La alarma DLQ suma mensajes visibles y no visibles. Usa dos de tres muestras para tolerar una publicación retrasada cuando `FILL` completa valores ausentes. [Comportamiento de FILL en CloudWatch](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/using-metric-math.html). Las métricas SQS son aproximadas y su antigüedad no equivale a contar las órdenes pendientes en SQL. [Métricas de SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-available-cloudwatch-metrics.html).

Las alarmas tienen acciones externas deshabilitadas: muestran `OK`, `ALARM` o `INSUFFICIENT_DATA` en CloudWatch. No envían correo, no agregan SNS ni cambian la capacidad de los servicios. La prueba exige una DLQ vacía, introduce una sonda técnica que no es una compra, espera `OK → ALARM`, retira solo esa sonda y espera `OK` en las nueve alarmas. No usa `SetAlarmState` para simular el resultado. El informe queda en `.local/aws-monitoring-smoke.json`. Las alarmas y el panel pueden consumir crédito del laboratorio.

### Publicar después de las pruebas

Cada cambio en `develop` ejecuta `Run tests`. Los runners de GitHub levantan sus propios contenedores de Floci y dependencias; sus recursos son temporales. El Floci del computador se actualiza al ejecutar de nuevo `start-local-purchase.ps1 -Build`. Un runner de GitHub no puede llegar al `localhost` del computador.

El job final `Deploy tested develop to Learner Lab` llama a `deploy-aws.yml` cuando las pruebas E2E y de producción pasan y la variable de repositorio `AWS_DEPLOY_ENABLED` vale exactamente `true`. Los errores de otros jobs también impiden publicar. Los pull requests nunca ejecutan el despliegue. Sin esa variable, el despliegue queda omitido y las pruebas siguen funcionando.

Antes de activarlo, deben existir los cinco repositorios ECR (cuatro servicios y procesador Lambda), el clúster y los cuatro servicios ECS, las dependencias, la función Lambda de imagen con su entorno/VPC y el vínculo de SQS habilitado con `BatchSize=1` apuntando a la función sin alias. El JSON `AWS_DEPLOY_CONFIG` incluye `purchaseRepository: "eventpass/purchase-lambda"` y `purchaseFunction: "eventpass-process-purchase-image"`. Los servicios usan ECS con circuit breaker y rollback y están conectados al ALB interno y al API Gateway. El pipeline parte de ese entorno conectado y reconcilia su configuración: prepara los cinco repositorios ECR, colas, DynamoDB y logs; aplica los stacks de red y datos y ejecuta el contenedor temporal SQL/Redis. Después de publicar configura CloudWatch. Los change sets rechazan eliminaciones y reemplazos. No es un instalador desde una cuenta vacía: los servicios ECS, la función Lambda y su vínculo se preparan antes con los pasos de esta guía.

La configuración actual usa Secrets y variables de repositorio. Esto permite configurarla como colaborador; los Secrets de un Environment de un repositorio personal requieren al propietario. El job identifica sus despliegues con el Environment `aws-lab`, que GitHub crea al usarlo si todavía no existe. Las protecciones que configure el propietario en ese Environment siguen aplicándose. El workflow reutilizable recibe los Secrets mediante `secrets: inherit`.

| Tipo | Nombre | Contenido |
| --- | --- | --- |
| Variable del repositorio | `AWS_DEPLOY_CONFIG` | JSON completo basado en `deploy-config.example.json`, con la cuenta y recursos reales. |
| Variable del repositorio | `AWS_TASK_CONFIG` | JSON completo basado en `task-config.example.json`, con roles, direcciones privadas y referencias ARN de secretos reales. Los digests de imágenes se reemplazan durante la publicación. |
| Secret del repositorio | `AWS_ACCESS_KEY_ID` | Access key temporal obtenida en AWS Details de la sesión de laboratorio. |
| Secret del repositorio | `AWS_SECRET_ACCESS_KEY` | Secret key temporal de esa misma sesión. |
| Secret del repositorio | `AWS_SESSION_TOKEN` | Session token temporal de esa misma sesión. |
| Variable del repositorio | `AWS_DEPLOY_ENABLED` | `true` solo después de verificar la conexión y recursos de la sala. |

No copies contraseñas ni tokens a los JSON o al repositorio. Las contraseñas de las aplicaciones se referencian por ARN; sus valores permanecen en Secrets Manager/SSM. Las credenciales de AWS Academy caducan y requieren renovar los tres Secrets cuando cambie la sesión. El inicio del laboratorio sigue siendo necesario. Si se dispone de un rol federado de GitHub mediante OIDC, puede sustituirse esta conexión temporal.

Con GitHub CLI instalado, o su copia local verificada en `.local/tools/gh.exe`, ejecuta desde la raíz del proyecto:

```powershell
./infra/aws/connect-learner-lab.ps1
./infra/aws/configure-github-lab.ps1 -EnableDeployment
```

El segundo comando verifica la cuenta AWS y los JSON locales, usa la sesión GitHub de Git Credential Manager y envía las credenciales a GitHub CLI por entrada estándar, sin escribir valores en archivos ni argumentos. GitHub CLI cifra los Secrets para el repositorio. Deshabilita temporalmente la publicación mientras reemplaza los tres valores y actualiza las dos variables de configuración; solo la habilita al terminar si recibió `-EnableDeployment`. Sin ese parámetro queda deshabilitada. No cambia los Secrets existentes de JWT o clientes internos. [GitHub CLI oficial](https://github.com/cli/cli/releases).

Después de renovar la sesión del laboratorio, repite ambos comandos para actualizar también GitHub. Las credenciales cifradas del PC no se actualizan automáticamente en GitHub. Si el propietario configura Secrets del mismo nombre en `aws-lab`, estos tendrán prioridad y deberán mantenerse coherentes.

`deploy-lab.mjs --check` valida las definiciones, la identidad, la existencia de ECR/ECS/Lambda, el vínculo SQS y una prueba del Gateway sin publicar imágenes ni actualizar aplicaciones. Requiere AWS CLI, Node y credenciales vigentes, además de `AWS_DEPLOY_CONFIG`, `AWS_TASK_CONFIG` y `DEPLOY_SHA` en el entorno. Comprueba recursos accesibles, pero no garantiza permisos de escritura como `iam:PassRole`; estos se ejercen al publicar.

La ejecución completa comprueba que sigue desplegando el último commit de `develop`, construye las cinco imágenes Docker para Linux x86-64, las publica con una etiqueta que incluye el commit y un identificador de ejecución, y obtiene sus digests. Esto permite repetir el mismo commit con repositorios ECR de etiquetas inmutables. Genera y registra nuevas tareas, actualiza Lambda con `--image-uri`, cambia los servicios ECS y espera su estabilidad. Verifica también que ECS conserva la nueva definición: un rollback a la versión anterior hace fallar el job. Al terminar, prueba catálogo público, rechazo de órdenes sin sesión y bloqueo de la ruta interna de tokens por el Gateway.

Después ejecuta las mismas pruebas autenticadas del laboratorio desde el runner: sesión, roles, revocación, creación/cancelación de orden, pago simulado, aforo lleno, devolución simulada, idempotencia y uso del ticket. `Invoke-RunnerAws.ps1` utiliza AWS CLI con las credenciales del job; no necesita el archivo cifrado de Windows. La preparación exige la cuenta correcta, Lambda de imagen activa y su vínculo a la cola de compras. Las pruebas crean datos identificables, cancelan los eventos al terminar y cierran sesiones. Un fallo en ellas marca el despliegue como fallido; no implica un rollback automático de los cinco componentes.

Tras un despliegue de GitHub, puedes actualizar los identificadores locales antes de comprobar las tareas manualmente:

```powershell
./infra/aws/Sync-LabServiceState.ps1
```

La sincronización consulta las cuatro definiciones estables y sus imágenes actuales, valida red privada y cuenta, y actualiza solo los informes locales. No publica ni modifica servicios, y no certifica que esas versiones correspondan a un commit concreto. Las publicaciones manuales parciales y el arranque de Lambda la usan para conservar las versiones de servicios que pudo actualizar GitHub. Evita superponer publicaciones manuales con un despliegue en curso.

La URL del Gateway, VPC Link, listener y target groups permanecen asociados a los servicios ECS: las tareas nuevas reciben tráfico a través del mismo acceso. Cambiar `routes.json`, la red, esquemas de secretos o la configuración de Lambda requiere actualizar la infraestructura correspondiente; este pipeline publica imágenes, variables de tareas y código de Lambda. Las variables y permisos de Lambda deben prepararse antes. El despliegue de los cuatro servicios y Lambda no es una transacción: sus contratos deben ser compatibles durante la actualización, y los cambios SQL deben permitir convivencia entre versiones. Para volver a una versión anterior se necesita una migración compatible y las definiciones/digests anteriores.

En GitHub Actions se añaden pruebas del controlador de despliegue con respuestas AWS simuladas: cuenta incorrecta, recursos incompletos, falta de rollback, Gateway caído, publicación por digest, ejecución antigua y rollback de ECS. Estas pruebas validan la lógica del pipeline; la conectividad real con la sala se confirma con el preflight y el primer despliegue.
