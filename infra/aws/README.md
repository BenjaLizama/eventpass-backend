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
