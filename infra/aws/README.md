# Preparación de EventPass para ECS

Auth, Eventos, Órdenes y Tickets siguen siendo aplicaciones Spring Boot. Maven compila cada JAR, Docker lo incorpora a una imagen, ECR almacena esa imagen y ECS ejecuta el contenedor. Este directorio prepara las definiciones de tarea. La red, los servicios ECS y las integraciones requieren los identificadores y permisos de la sala.

## Preparar las imágenes y tareas

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
3. Empaqueta `index.mjs` y `process-order.mjs` de `infra/purchase-lambda` en la raíz de un ZIP. Configura en Lambda `AUTH_SERVICE_URL`, `EVENTS_SERVICE_URL`, `ORDERS_SERVICE_URL`, `TICKETS_SERVICE_URL` y las credenciales del cliente interno usadas por Auth. Vincula la cola de compras pendientes mediante un event source mapping. Valida acceso a la VPC y permisos de SQS y CloudWatch.
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

### Publicar después de las pruebas

Cada cambio en `develop` ejecuta `Run tests`. Los runners de GitHub levantan sus propios contenedores de Floci y dependencias; sus recursos son temporales. El Floci del computador se actualiza al ejecutar de nuevo `start-local-purchase.ps1 -Build`. Un runner de GitHub no puede llegar al `localhost` del computador.

El job final `Deploy tested develop to Learner Lab` llama a `deploy-aws.yml` cuando las pruebas E2E y de producción pasan y la variable de repositorio `AWS_DEPLOY_ENABLED` vale exactamente `true`. Los errores de otros jobs también impiden publicar. Los pull requests nunca ejecutan el despliegue. Sin esa variable, el despliegue queda omitido y las pruebas siguen funcionando.

Antes de activarlo, deben existir los cuatro repositorios ECR, el clúster y los cuatro servicios ECS, las dependencias, la función Lambda con su entorno/VPC y el vínculo de SQS habilitado con `BatchSize=1` apuntando a la función sin alias. Los servicios deben usar el controlador `ECS` con circuit breaker y rollback habilitados. Deben estar conectados al ALB interno y al API Gateway. Este pipeline actualiza aplicaciones sobre esa infraestructura existente; no la aprovisiona.

En GitHub, crea el Environment `aws-lab` y configura:

| Tipo | Nombre | Contenido |
| --- | --- | --- |
| Variable del Environment | `AWS_DEPLOY_CONFIG` | JSON completo basado en `deploy-config.example.json`, con la cuenta y recursos reales. |
| Variable del Environment | `AWS_TASK_CONFIG` | JSON completo basado en `task-config.example.json`, con roles, direcciones privadas y referencias ARN de secretos reales. Los digests de imágenes se reemplazan durante la publicación. |
| Secret del Environment | `AWS_ACCESS_KEY_ID` | Access key temporal obtenida en AWS Details de la sesión de laboratorio. |
| Secret del Environment | `AWS_SECRET_ACCESS_KEY` | Secret key temporal de esa misma sesión. |
| Secret del Environment | `AWS_SESSION_TOKEN` | Session token temporal de esa misma sesión. |
| Variable del repositorio | `AWS_DEPLOY_ENABLED` | `true` solo después de verificar la conexión y recursos de la sala. |

No copies contraseñas ni tokens a los JSON o al repositorio. Las contraseñas de las aplicaciones se referencian por ARN; sus valores permanecen en Secrets Manager/SSM. Las credenciales de AWS Academy caducan y requieren renovar los tres Secrets cuando cambie la sesión. El inicio del laboratorio sigue siendo necesario. Si se dispone de un rol federado de GitHub mediante OIDC, puede sustituirse esta conexión temporal.

`deploy-lab.mjs --check` valida las definiciones, la identidad, la existencia de ECR/ECS/Lambda, el vínculo SQS y una prueba del Gateway sin publicar imágenes ni actualizar aplicaciones. Requiere AWS CLI, Node y credenciales vigentes, además de `AWS_DEPLOY_CONFIG`, `AWS_TASK_CONFIG` y `DEPLOY_SHA` en el entorno. Comprueba recursos accesibles, pero no garantiza permisos de escritura como `iam:PassRole`; estos se ejercen al publicar.

La ejecución completa comprueba que sigue desplegando el último commit de `develop`, construye las cuatro imágenes para Linux x86-64, las publica con una etiqueta que incluye el commit y un identificador de ejecución, y obtiene sus digests. Esto permite repetir el mismo commit con repositorios ECR de etiquetas inmutables. Genera y registra nuevas tareas, actualiza el ZIP de Lambda, cambia los servicios ECS y espera su estabilidad. Verifica también que ECS conserva la nueva definición: un rollback a la versión anterior hace fallar el job. Al terminar, prueba catálogo público, rechazo de órdenes sin sesión y bloqueo de la ruta interna de tokens por el Gateway.

La URL del Gateway, VPC Link, listener y target groups permanecen asociados a los servicios ECS: las tareas nuevas reciben tráfico a través del mismo acceso. Cambiar `routes.json`, la red, esquemas de secretos o la configuración de Lambda requiere actualizar la infraestructura correspondiente; este pipeline publica imágenes, variables de tareas y código de Lambda. Las variables y permisos de Lambda deben prepararse antes. El despliegue de los cuatro servicios y Lambda no es una transacción: sus contratos deben ser compatibles durante la actualización, y los cambios SQL deben permitir convivencia entre versiones. Para volver a una versión anterior se necesita una migración compatible y las definiciones/digests anteriores.

En GitHub Actions se añaden pruebas del controlador de despliegue con respuestas AWS simuladas: cuenta incorrecta, recursos incompletos, falta de rollback, Gateway caído, publicación por digest, ejecución antigua y rollback de ECS. Estas pruebas validan la lógica del pipeline; la conectividad real con la sala se confirma con el preflight y el primer despliegue.
