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
