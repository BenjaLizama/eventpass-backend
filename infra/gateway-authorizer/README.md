# Validación de JWT y roles en API Gateway

Los microservicios siguen en Spring Boot. API Gateway usa un **Lambda REQUEST authorizer** escrito en Java 21 y construido con este Dockerfile. Recibe el Bearer y la ruta, consulta `/api/v1/auth/me` de Auth por el balanceador privado y aplica `route-policy.json`. Solo permite una petición después de que Auth valide firma, vencimiento, propósito `ACCESS`, cuenta y sesión Redis.

La HTTP API de AWS admite RSA en su autorizador JWT incorporado; nuestro emisor usa HMAC. Este autorizador Lambda permite conservar el emisor y comprobar revocación y rol actual en cada petición. No es el autorizador JWT incorporado ni utiliza Cognito. [Tipos de JWT admitidos](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-jwt-authorizer.html), [autorizadores Lambda](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-lambda-authorizer.html).

## Privilegios externos

| Operación | Quién puede acceder |
| --- | --- |
| Registro, login, refresh y catálogo publicado/recintos | Público. Refresh verifica el token de renovación en Auth. |
| Perfil propio y logout | Usuario autenticado con sesión activa. |
| Crear, publicar o modificar eventos | ADMIN u ORGANIZER; el organizador solo modifica sus eventos. |
| Crear recintos | ADMIN u ORGANIZER. |
| Crear STAFF u ORGANIZER | ADMIN. El registro público siempre crea CUSTOMER. |
| Crear, pagar, consultar y cancelar órdenes | CUSTOMER; solo sus órdenes. |
| Consultar tickets | CUSTOMER; solo los propios. |
| Marcar un ticket como usado | STAFF. CUSTOMER no puede autovalidarse; ADMIN y ORGANIZER tampoco heredan este acceso. |
| Emitir tickets, reservar aforo y obtener tokens de servicio | Rutas internas, sin publicación externa en el Gateway. |

Las rutas desconocidas se deniegan. No se confía en headers de identidad enviados por el cliente. Los microservicios siguen validando el token y la propiedad del recurso: el autorizador no sustituye esos controles. Los borradores no se muestran a usuarios anónimos ni a otros organizadores. Los refresh y los tokens SERVICE no sirven como acceso externo.

El TTL del autorizador es **cero**: no se reutiliza una autorización de otra ruta ni se conserva acceso después del logout. Si Auth, Redis, la red o el formato del token fallan, se deniega el acceso. Un header ausente devuelve 401; una denegación del autorizador puede devolver 403. Ninguno se considera acceso autorizado. El autorizador no registra Bearers ni lleva el secreto de firma en su imagen o entorno.

Cambiar rol, estado o eliminar un usuario revoca sus sesiones mediante un índice por usuario en Redis, sin recorrer las sesiones de los demás. Los tokens emitidos antes de distinguir `ACCESS`/`REFRESH` requieren volver a iniciar sesión. El rol STAFF es global en el modelo actual; asignar validadores a eventos concretos requeriría un modelo adicional de asignaciones.

## Docker, pruebas y AWS

```powershell
docker build --platform linux/amd64 --provenance=false -t eventpass/gateway-authorizer:verify infra/gateway-authorizer
# El build ejecuta las pruebas Java, incluyendo rechazo por rol y fallo de Auth.
./infra/aws/start-lab-authorizer.ps1
./infra/aws/start-lab-network.ps1 -Apply
./infra/aws/test-lab-security.ps1
```

La red debe prepararse primero con `start-lab-network.ps1 -Apply`; proporciona un security group exclusivo del autorizador cuya salida de aplicación permite solo Auth por el puerto 8081 del ALB. El pipeline hace la preparación de red, despliega los microservicios, publica esta imagen y conecta las rutas. Actualizaciones siguientes conservan esa conexión en CloudFormation. Las pruebas E2E verifican el propósito del token, firmas alteradas, privilegios CUSTOMER y propiedad de eventos entre dos organizadores. La compra y el aislamiento de tickets se prueban también con las pruebas existentes.

Floci y Swarm comprueban los controles de Spring y los flujos funcionales. La conexión de este autorizador se verifica en la HTTP API real de AWS; no se presenta la emulación como evidencia de su ejecución en AWS.

## Roles IAM del laboratorio

CUSTOMER, STAFF, ORGANIZER, SUPPORT y ADMIN son roles de EventPass. No conceden permisos IAM ni credenciales AWS a los usuarios. API Gateway solo recibe permiso para invocar esta Lambda desde su API y autorizador concretos, con cuenta de origen restringida.

El laboratorio utiliza el `LabRole` existente para la ejecución; ese rol es compartido y sus permisos los controla AWS Academy. **Esto no equivale a un IAM de mínimo privilegio por componente.** En una cuenta con permisos de administración deben separarse los roles de Auth, Eventos, Órdenes, Tickets y las Lambdas. El autorizador necesita logs y administración de sus interfaces VPC; su código no necesita leer secretos, colas, DynamoDB ni administrar ECS. No se modifica `LabRole`, porque afectaría a los demás recursos de la sala.
