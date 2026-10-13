# Pruebas E2E de EventPass

Las pruebas E2E validan los flujos críticos entre `ms-auth`, `ms-events`,
`ms-orders`, `ms-tickets`, LocalStack/SQS, PostgreSQL y MongoDB.

## Requisitos

Levantar la plataforma y reconstruir los servicios modificados:

```bat
docker compose --env-file .env up -d --build
```

Definir las credenciales del administrador usado por las pruebas:

```bat
set "E2E_ADMIN_EMAIL=admin@eventpass.cl"
set "E2E_ADMIN_PASSWORD=Cambiame123!"
```

Ejecutar toda la suite E2E:

```bat
.\ms-auth\mvnw.cmd -f e2e-tests/pom.xml "-Dit.test=EventPassPurchaseFlowIT,OrderCancellationRestoresCapacityIT,OrderPerUserLimitIT,OrderInsufficientCapacityIT,TicketAccessControlIT" verify
```

## Cobertura actual

| Test | Flujo validado |
|---|---|
| `EventPassPurchaseFlowIT` | Login, creación de evento, orden, pago, emisión asíncrona de tickets, uso por `STAFF`, rechazo de reutilización y persistencia de `USED`. |
| `OrderCancellationRestoresCapacityIT` | Creación de orden `PENDING`, reserva de aforo, cancelación, devolución de aforo e impedimento de pago posterior. |
| `OrderPerUserLimitIT` | Respecto del límite `maxPerUser`: una segunda compra que lo exceda debe rechazarse sin alterar el aforo. |
| `OrderInsufficientCapacityIT` | Prevención de sobreventa: cuando el aforo llega a cero, nuevos intentos de compra deben rechazarse. |
| `TicketAccessControlIT` | Privacidad y RBAC: solo el dueño consulta su ticket, solo `STAFF` puede usar tickets y el ticket usado persiste como `USED`. |

## Arreglos detectados y corregidos

### 1. Respuesta incorrecta al usar un ticket

`TicketRepositoryCustomImpl.markAsUsed` actualizaba el ticket de `ACTIVE` a `USED`,
pero `findAndModify` devolvía el documento **anterior** a la actualización.

Antes:

```text
MongoDB actualiza ACTIVE → USED
findAndModify devuelve ACTIVE
PATCH responde ACTIVE
Test espera USED → falla
```

Después:

```text
MongoDB actualiza ACTIVE → USED
findAndModify devuelve USED
PATCH responde USED
Test continúa
```

Solución aplicada:

```java
return mongoTemplate.findAndModify(
        query,
        update,
        FindAndModifyOptions.options().returnNew(true),
        TicketDocument.class
);
```

Tras este cambio fue necesario reconstruir la imagen:

```bat
docker compose --env-file .env up -d --build ms-tickets
```

### 2. Autorización por roles no aplicada en ms-tickets

El E2E `TicketAccessControlIT` detectó que un cliente dueño de un ticket podía
ejecutar `PATCH /api/v1/tickets/{ticketCode}/use` y recibir `200`, pese a que el
endpoint declaraba `@PreAuthorize("hasRole('STAFF')")`.

Causa: `SecurityConfig` no tenía `@EnableMethodSecurity`, por lo que Spring no
evaluaba las anotaciones `@PreAuthorize`.

Solución aplicada:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
```

Comportamiento correcto validado:

```text
CUSTOMER ajeno no puede ver el ticket → 404
CUSTOMER no puede usar tickets → 403
CUSTOMER dueño no puede usar su propio ticket → 403
STAFF sí puede usar el ticket → 200
```

Tras este cambio también fue necesario reconstruir:

```bat
docker compose --env-file .env up -d --build ms-tickets
```

## Estado esperado

```text
EventPassPurchaseFlowIT              ✅
OrderCancellationRestoresCapacityIT  ✅
OrderPerUserLimitIT                  ✅
OrderInsufficientCapacityIT          ✅
TicketAccessControlIT                ✅
```