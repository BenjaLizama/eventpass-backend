# Plan de trabajo para EventPass en AWS

## Material de la asignatura

- `repomix-output.xml` contiene el caso EventPass y la pauta EP2. El caso exige aceptar la compra como pendiente de emisión, verificar el aforo en el procesamiento asíncrono, procesar las órdenes de SQS con Lambda y acceder a los microservicios por Amazon API Gateway sin exponerlos directamente. La EP2 sirve para evaluar la base de microservicios en local.
- `Docker, Swarm y AWS (1).html` explica Docker, Swarm y el despliegue de un servicio de ejemplo mediante EC2 y ECR en Learner Lab. Sirve como guía de infraestructura. Los dos archivos originales están en Descargas.
- El análisis pegado en el chat propone cómo adaptar esa guía a EventPass. VPC Link, el balanceador interno y Swarm quedan sujetos a los requisitos de la actividad y a los permisos del laboratorio.

## Punto de partida verificado

El 13 de noviembre de 2026, los puertos locales 8081 a 8084 respondieron HTTP 200 y Maven terminó con cinco pruebas E2E aprobadas. Cubrieron compra y emisión por SQS, cancelación y restitución de aforo, aforo insuficiente, límite por usuario y acceso a tickets. Los pasos para repetirlas están en [E2E_TESTS.MD](e2e-tests/E2E_TESTS.MD).

El [Compose actual](docker-compose.yml) usa LocalStack 3.4.0. En el flujo comprobado, Órdenes reserva al crear la orden, publica en SQS después del pago y Tickets consume la cola. La suite sirve de referencia para adaptar las pruebas al flujo requerido.

## Referencias de emulación local

- [Anuncio de AWS Emulator compartido por el profesor](https://aws.amazon.com/es/about-aws/whats-new/2026/11/aws-emulator-now-available/) y [versión en inglés verificada](https://aws.amazon.com/about-aws/whats-new/2026/11/aws-emulator-now-available/). AWS documenta que las operaciones sin implementar devuelven HTTP 501 y que el estado se pierde al detener el emulador.
- [Repositorio oficial de AWS Emulator](https://github.com/aws/aws-emulator), con acceso a la documentación y al informe de fidelidad de las API.
- [Sitio de Floci](https://floci.io/) y [organización Floci en GitHub](https://github.com/floci-io). Su emulador de AWS anuncia compatibilidad con SQS y Lambda.

Floci 2.4.0 se usa en la primera prueba local de API Gateway. AWS Emulator queda como alternativa para comparar la cobertura de operaciones de SQS y Lambda. La integración privada y los permisos efectivos se validan en Learner Lab.

### Primera prueba local de API Gateway

La HTTP API de Floci enruta llamadas de cliente a los cuatro servicios mediante integraciones HTTP y la red de Compose. La lista de rutas preserva el catálogo, el login y la compra. Las operaciones internas de token de servicio y aforo quedan fuera de esa lista.

## Flujo objetivo de compra y devolución simuladas

La confirmación registra de inmediato la orden como aceptada y pendiente de emisión, con el pago simulado. La orden se encola en SQS para que Lambda procese las compras de eventos masivos de forma asíncrona. Lambda solicita la reserva a Eventos y, si hay cupo, solicita la emisión a Tickets.

Si el aforo está lleno al procesar la orden, se registra como rechazada por falta de cupo y se registra la devolución simulada del importe completo. La orden queda sin ticket y su estado permite informar al comprador del motivo y de la devolución. Los reintentos del mensaje deben conservar el resultado y evitar reservas, emisiones o devoluciones duplicadas. Para esta entrega, el cobro y la devolución se simulan dentro del backend, sin configurar una pasarela de pago.

## Etapas según el diagrama de arquitectura V2

1. **Base local.** Conservar el flujo vigente mientras se valida la publicación recuperable desde PostgreSQL y la reserva de aforo idempotente por ítem de orden. Repetir las pruebas entre servicios con Compose y LocalStack.
2. **Entrada local por API Gateway.** Usar Floci para HTTP API v2 y un conjunto explícito de métodos y rutas. Probar autenticación, catálogo y el ciclo de compra desde la URL del gateway. Spring Security valida el JWT HMAC, los roles y la sesión activa. Mantener las operaciones de servicio fuera de la API pública. [API Gateway en Floci](https://floci.io/floci/services/api-gateway/)
3. **Procesador Lambda local.** Encolar la orden pendiente tras el pago simulado. Lambda coordina la reserva por ítem, la emisión y el estado final. La devolución simulada y la emisión deben conservar el mismo resultado ante mensajes repetidos. Probar envío interrumpido, el último cupo, reintentos y mensajes enviados a la cola de errores. Floci permite probar el disparador SQS; ejecutar también invocaciones concurrentes para cubrir el último cupo. [Lambda en Floci](https://floci.io/floci/services/lambda/) · [Configuración SQS en Lambda](https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-configure.html)
4. **Persistencia del diagrama.** El objetivo ilustra Aurora para Auth, Eventos y Órdenes y DynamoDB para Tickets. Hoy Compose usa PostgreSQL y MongoDB. Antes de cambiar la persistencia, verificar arranque con esquema vacío, unicidad por ítem e índice de ticket y uso atómico de un ticket. Incorporar Redis privado para conservar la revocación de sesiones.
5. **Sala del profesor.** El diagrama ubica los cuatro servicios en un clúster ECS y un ALB interno. La HTTP API llega al listener del ALB mediante VPC Link V2. El ALB requiere subredes de dos zonas; usar target groups de tipo `ip` si las tareas ECS usan `awsvpc`. Confirmar permisos para ECS, balanceador, VPC Link, SQS, Lambda, bases e IAM antes de provisionar. Si la pauta exige validación de JWT en Gateway, decidir entre un autorizador Lambda que consulte la sesión y un cambio a firma RSA con JWKS. [Integración privada](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-private.html) · [ALB](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/application-load-balancers.html) · [ECS](https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_LoadBalancer.html) · [Autorizador JWT](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-jwt-authorizer.html)

En el perfil de desarrollo, Órdenes crea `order_completed_outbox` al arrancar. Para el perfil de producción, aplicar [el SQL de la outbox](ms-orders/src/main/resources/db/order-completed-outbox.sql) y [el SQL de reservas de Eventos](ms-events/docs/sql/001-capacity-reservations.sql) al preparar PostgreSQL. El publicador puede volver a enviar un evento si el proceso se interrumpe después de que SQS lo recibe. Tickets usa el identificador del ítem y el índice del ticket para evitar una segunda emisión.

El [arranque del Gateway local](infra/gateway/README.md) documenta cómo levantar Floci en paralelo con LocalStack y ejecutar la suite funcional desde una sola URL.
