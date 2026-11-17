# Compra asíncrona local

Desde la raíz, con Docker Desktop listo, ejecuta `.\infra\purchase-lambda\start-local-purchase.ps1` en PowerShell 7. El script levanta EventPass en modo privado y configura la cola, Lambda y Swagger en Floci. Abre [Swagger](http://127.0.0.1:8090/).

Agrega `-Build` la primera vez o después de editar código Java. Termina las compras y espera a que se emitan sus tickets antes de cambiar de modo. El consumidor de Tickets usa LocalStack en el modo anterior y Floci en el modo asíncrono. Eliminar el contenedor de Floci borra sus colas locales.

El comprador crea una orden `AWAITING_PAYMENT`. El pago simulado devuelve `PENDING_ISSUANCE`. Consulta la orden para ver el resultado. Lambda solicita el aforo por ítem y emite el ticket si se confirma la reserva. La orden queda `PAID`. Si Eventos rechaza la reserva, queda `REJECTED_NO_CAPACITY` con pago `REFUNDED`. Se devuelve el importe completo de forma simulada.

El arranque habitual de la demo activa el perfil anterior. Las órdenes anteriores conservan su mecanismo de reserva y liberación. Para reanudar la compra asíncrona después de reiniciar Floci, ejecuta este script de nuevo. Floci mantiene los recursos de esta demo durante la vida del contenedor.

## Comprobar mensajes duplicados

La prueba crea un evento, emite entradas con una compra y simula la devolución de otra cuando se agota el aforo. Después publica otra vez ambas órdenes en la cola local y espera a que Lambda consuma los mensajes. Comprueba que siguen los mismos códigos de ticket, el mismo aforo y los mismos estados finales.

Desde PowerShell 7, con el modo asíncrono levantado, define `E2E_ADMIN_EMAIL` y `E2E_ADMIN_PASSWORD` con tu cuenta de prueba. Luego ejecuta:

```powershell
$env:E2E_ASYNC_PURCHASE = 'true'
$env:AUTH_BASE_URL = $env:EVENTS_BASE_URL = $env:ORDERS_BASE_URL = $env:TICKETS_BASE_URL = 'http://127.0.0.1:8090'
.\ms-auth\mvnw.cmd -f e2e-tests/pom.xml '-Dit.test=EventPassPurchaseFlowIT' verify
```

Para explicarlo en video, muestra la respuesta del pago con `PENDING_ISSUANCE` y luego consulta la orden hasta ver su estado final. SQS puede entregar un mensaje más de una vez. Eventos identifica la reserva por ítem, Tickets identifica cada entrada por ítem e índice y Órdenes mantiene el resultado ya registrado.

La misma prueba envía dos pagos concurrentes por una localidad de un solo cupo. Para probar también la recuperación ante una interrupción, agrega `$env:E2E_INTERRUPT_TICKETS = 'true'` antes de ejecutarla. En ese modo, la prueba detiene el contenedor local de Tickets, comprueba que se reservó el cupo y lo vuelve a iniciar. El resultado esperado es un ticket emitido y una devolución simulada.

## Fallo persistente y recuperación

SQS mueve las compras que agotan cinco entregas a `eventpass-purchase-errors`. La cola de errores conserva los mensajes durante 14 días mientras Floci mantiene sus datos. Si la emisión falla, la orden y su reserva quedan pendientes. Una devolución simulada corresponde al rechazo de aforo.

Después de corregir la causa y comprobar que Tickets responde, devuelve los mensajes a la cola de compra:

```powershell
.\infra\purchase-lambda\recover-local-purchases.ps1 -Confirm
```

El comando inicia una tarea de SQS que mueve todos los mensajes de esta cola de errores local. El resultado de la compra se comprueba consultando la orden y sus tickets.

Para probar el fallo persistente, usa un entorno local sin compras en curso y una cola de errores vacía. Arranca con `start-local-purchase.ps1 -VisibilityTimeout 25 -MaxReceiveCount 2`, configura las variables de la prueba anterior, activa `E2E_INTERRUPT_TICKETS` y `E2E_PURCHASE_DLQ` con el valor `true` y ejecuta `EventPassPurchaseFlowIT`. La prueba mantiene Tickets detenido hasta encontrar el mensaje en la cola de errores, restaura el servicio y ejecuta la recuperación. Al terminar, vuelve a ejecutar el arranque sin esos parámetros para recuperar la política habitual.
