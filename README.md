# 🎟️ EventPass - Arquitectura de emisión de tickets distribuida

**EventPass** es un sistema distribuido de alta concurrencia diseñado para gestionar la venta, reserva, emisión y validación de entradas para eventos masivos, priorizando el rendimiento y la escalabilidad durante picos de tráfico extremo.

La arquitectura se compone de microservicios desacoplados que aplican el patrón *Database per Service*, combinando comunicación síncrona vía REST APIs para consultas de bajo costo y procesamiento asíncrono basado en colas de mensajería para asegurar que las transacciones complejas no colapsen el sistema.

### 🏛️ Principios de Arquitectura
* **Desacoplamiento de Dominios:** Cada microservicio es dueño absoluto de sus datos y lógica de negocio.
* **Seguridad Stateless:** Autenticación y autorización centralizada mediante tokens JWT con Control de Acceso Basado en Roles (RBAC).
* **Procesamiento Asíncrono de Órdenes:** Desacoplamiento entre la recepción del pedido y la reserva de inventario para absorber picos repentinos de demanda.
* **Resiliencia y Escalabilidad:** Aislamiento de fallos para evitar caídas en cascada a través de la infraestructura.

### 📦 Servicios de Dominio
* **`ms-auth`**: Gestión de identidades, perfiles de usuario y emisión de credenciales de acceso.
* **`ms-events`**: Control del catálogo de eventos, gestión de fechas y actualización de aforo en tiempo real.
* **`ms-orders`**: Orquestación de compras, recepción de intenciones de reserva y trazabilidad de estados de orden.
* **`ms-tickets`**: Generación de entradas con identificadores únicos (QR/Hash) y verificación de acceso en puerta.