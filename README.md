# 🎟️ EventPass

**EventPass** es una plataforma distribuida para la gestión de eventos masivos, diseñada bajo una arquitectura de **microservicios** y orientada a escenarios de alta concurrencia.

El sistema permite gestionar usuarios, eventos, recintos, órdenes de compra y emisión/validación de entradas, utilizando servicios desacoplados y bases de datos independientes.

La arquitectura aplica el patrón **Database per Service**, comunicación REST para operaciones síncronas y **AWS SQS** para procesamiento asíncrono.

---

## 🏛️ Arquitectura

EventPass está compuesto por cuatro microservicios independientes:

```text
                         ┌──────────────────┐
                         │     Cliente      │
                         └────────┬─────────┘
                                  │
                     ┌────────────┴────────────┐
                     │                         │
                     ▼                         ▼
              ┌─────────────┐          ┌─────────────┐
              │   ms-auth   │          │  ms-events  │
              │    :8081    │          │    :8082    │
              └──────┬──────┘          └──────┬──────┘
                     │                        │
                     ▼                        ▼
               PostgreSQL                PostgreSQL
                auth DB                 events DB


                     ┌─────────────┐
                     │  ms-orders  │
                     │    :8083    │
                     └──────┬──────┘
                            │
                            │ OrderCompletedEvent
                            ▼
                     ┌─────────────┐
                     │     SQS     │
                     └──────┬──────┘
                            │
                            ▼
                     ┌─────────────┐
                     │ ms-tickets  │
                     │    :8084    │
                     └──────┬──────┘
                            │
                            ▼
                         MongoDB
```

### Principios principales

- **Microservicios desacoplados:** cada servicio posee una responsabilidad de negocio específica.
- **Database per Service:** cada microservicio administra su propia persistencia.
- **Seguridad centralizada:** autenticación y autorización mediante JWT y RBAC.
- **Comunicación asíncrona:** eventos de negocio mediante AWS SQS.
- **Alta concurrencia:** control de reservas y actualización segura del aforo.
- **Contenerización:** todos los servicios e infraestructura local se ejecutan mediante Docker Compose.
- **Configuración por ambientes:** perfiles `dev` y `prod`.

---

# 📦 Microservicios

## 🔐 ms-auth

Responsable de autenticación, autorización, usuarios y sesiones.

### Funcionalidades

- Registro e inicio de sesión.
- Generación y validación de JWT.
- Access tokens y refresh tokens.
- Gestión de sesiones mediante Redis.
- Blacklist y revocación de tokens.
- RBAC basado en roles.
- Gestión administrativa de usuarios.
- Estados de usuario.
- Protección de endpoints mediante Spring Security.
- Documentación OpenAPI.

### Roles

```text
ADMIN
STAFF
ORGANIZER
SUPPORT
CUSTOMER
```

### Persistencia

```text
PostgreSQL
    │
    └── eventpass_auth

Redis
    │
    ├── Sesiones
    └── Blacklist de tokens
```

---

## 🎫 ms-events

Responsable de la gestión del catálogo de eventos y sus recintos.

### Funcionalidades

- Creación y administración de recintos.
- Creación y administración de eventos.
- Categorías de eventos.
- Estados del ciclo de vida de un evento.
- Gestión de categorías de tickets.
- Control de capacidad.
- Reserva y liberación de aforo.
- Consulta de eventos propios del organizador.
- Control de concurrencia en operaciones de aforo.
- Integración con mensajería mediante SQS.

### Categorías de eventos

```text
CONCERT
FESTIVAL
THEATER
SPORTS
CONFERENCE
COMEDY
OTHER
```

### Estados

```text
DRAFT
PUBLISHED
PAUSED
CANCELLED
COMPLETED
```

### Persistencia

```text
PostgreSQL
    │
    └── eventpass_events
```

---

## 🛒 ms-orders

Responsable del ciclo de vida de las órdenes de compra.

### Funcionalidades

- Creación de órdenes.
- Validación de información de compra.
- Reserva de capacidad.
- Gestión de estados de órdenes.
- Procesamiento de pagos.
- Cancelación de órdenes.
- Liberación de capacidad.
- Consulta de órdenes del usuario.
- Publicación de eventos de negocio mediante SQS.

### Flujo principal

```text
Crear orden
     │
     ▼
Validar información
     │
     ▼
Reservar capacidad
     │
     ▼
Crear orden PENDING
     │
     ▼
Procesar pago
     │
     ▼
Orden completada
     │
     ▼
Publicar OrderCompletedEvent
```

### Persistencia

```text
PostgreSQL
    │
    └── eventpass_orders
```

---

## 🎟️ ms-tickets

Responsable de la emisión y validación de entradas.

A diferencia de los demás servicios, utiliza MongoDB debido a la naturaleza documental de la información asociada a los tickets.

### Funcionalidades

- Consumo de eventos de órdenes completadas.
- Generación de tickets.
- Persistencia de tickets.
- Generación de códigos únicos.
- Estados de tickets.
- Validación de tickets.
- Marcado de tickets como utilizados.
- Prevención de duplicados.
- Actualizaciones atómicas para operaciones concurrentes.

### Flujo

```text
OrderCompletedEvent
        │
        ▼
       SQS
        │
        ▼
OrderCompletedEventConsumer
        │
        ▼
TicketService
        │
        ▼
Generación de tickets
        │
        ▼
     MongoDB
```

### Estados

```text
ACTIVE
USED
```

### Persistencia

```text
MongoDB
    │
    └── eventpass_tickets
```

---

# 📨 Comunicación asíncrona

EventPass utiliza **AWS SQS** para desacoplar el procesamiento de órdenes y la emisión de tickets.

El flujo principal es:

```text
ms-orders
    │
    │ OrderCompletedEvent
    ▼
SQS
    │
    ▼
ms-tickets
    │
    ▼
MongoDB
```

Esto permite que `ms-orders` no tenga que esperar a que `ms-tickets` termine de generar las entradas.

## LocalStack

Durante el desarrollo local se utiliza **LocalStack** para simular los servicios de AWS.

```text
Desarrollo local:

ms-orders
    │
    ▼
LocalStack
    │
    ▼
SQS simulado
    │
    ▼
ms-tickets
```

LocalStack es exclusivamente parte de la infraestructura de desarrollo local. En un entorno AWS real, esta simulación se reemplaza por los servicios administrados de AWS.

---

# 🗄️ Infraestructura local

Docker Compose permite levantar toda la plataforma junto con sus dependencias.

```text
┌───────────────────────────────────────────────┐
│                Docker Compose                 │
│                                               │
│  ┌─────────┐  ┌──────────┐  ┌─────────────┐ │
│  │ ms-auth │  │ms-events │  │  ms-orders  │ │
│  │  :8081  │  │  :8082   │  │    :8083   │ │
│  └────┬────┘  └────┬─────┘  └──────┬──────┘ │
│       │             │               │        │
│  PostgreSQL    PostgreSQL      PostgreSQL    │
│                                               │
│  ┌────────────┐  ┌─────────┐  ┌───────────┐ │
│  │ ms-tickets │  │  Redis  │  │  MongoDB  │ │
│  │   :8084    │  │  :6379  │  │  :27018   │ │
│  └─────┬──────┘  └─────────┘  └───────────┘ │
│        │                                      │
│     LocalStack :4566                          │
│                                               │
└───────────────────────────────────────────────┘
```

### Servicios de infraestructura

| Servicio | Puerto local | Propósito |
|---|---:|---|
| `ms-auth` | `8081` | Autenticación y usuarios |
| `ms-events` | `8082` | Eventos y aforo |
| `ms-orders` | `8083` | Órdenes |
| `ms-tickets` | `8084` | Tickets |
| PostgreSQL Auth | `5432` | Base de datos de `ms-auth` |
| PostgreSQL Events | `5433` | Base de datos de `ms-events` |
| PostgreSQL Orders | `5434` | Base de datos de `ms-orders` |
| MongoDB Tickets | `27018` | Base de datos de `ms-tickets` |
| Redis | `6379` | Sesiones y cache |
| LocalStack | `4566` | Servicios AWS simulados |

---

# 🚀 Ejecución local

## Requisitos

- Docker
- Docker Compose
- Git

No es necesario instalar PostgreSQL, MongoDB, Redis o LocalStack directamente en el sistema para ejecutar la plataforma mediante Docker Compose.

---

## 1. Clonar el repositorio

```bash
git clone https://github.com/BenjaLizama/eventpass-backend
cd eventpass-backend
```

---

## 2. Configurar variables de entorno

Crear el archivo `.env` en la raíz del proyecto.

Las variables principales corresponden a:

```env
SPRING_PROFILES_ACTIVE=dev

JWT_SECRET=<secret>

AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test

POSTGRES_AUTH_DB=eventpass_auth
POSTGRES_AUTH_USER=postgres
POSTGRES_AUTH_PASSWORD=<password>
POSTGRES_AUTH_PORT=5432

POSTGRES_EVENTS_DB=eventpass_events
POSTGRES_EVENTS_USER=postgres
POSTGRES_EVENTS_PASSWORD=<password>
POSTGRES_EVENTS_PORT=5433

POSTGRES_ORDERS_DB=eventpass_orders
POSTGRES_ORDERS_USER=postgres
POSTGRES_ORDERS_PASSWORD=<password>
POSTGRES_ORDERS_PORT=5434

REDIS_PORT=6379
LOCALSTACK_PORT=4566

MONGODB_TICKETS_PORT=27018
MONGODB_TICKETS_DATABASE=eventpass_tickets
```

> Los valores sensibles no deben almacenarse directamente en el repositorio.

---

## 3. Levantar la plataforma

Desde la raíz:

```bash
docker compose up --build
```

Para ejecutarlo en segundo plano:

```bash
docker compose up --build -d
```

Docker Compose se encargará de:

1. Crear la red interna.
2. Crear los contenedores.
3. Levantar PostgreSQL.
4. Levantar MongoDB.
5. Levantar Redis.
6. Levantar LocalStack.
7. Inicializar los recursos AWS locales necesarios.
8. Construir los microservicios.
9. Esperar las dependencias mediante health checks.
10. Levantar los cuatro microservicios.

---

## 4. Verificar los contenedores

```bash
docker compose ps
```

Para revisar los logs:

```bash
docker compose logs -f
```

O individualmente:

```bash
docker compose logs -f ms-auth
docker compose logs -f ms-events
docker compose logs -f ms-orders
docker compose logs -f ms-tickets
```

---

## 5. Detener la plataforma

Para detener los contenedores:

```bash
docker compose down
```

Este comando mantiene los volúmenes y, por lo tanto, los datos persistidos.

Para realizar una limpieza completa:

```bash
docker compose down -v --remove-orphans
```

> `-v` elimina los volúmenes de Docker y, por lo tanto, también elimina los datos almacenados en las bases de datos.

---

# 🧰 Scripts de desarrollo

El directorio `scripts/` contiene scripts utilizados para inicializar recursos necesarios para el entorno local.

Su principal objetivo es preparar los recursos simulados de AWS utilizados por LocalStack, como las colas SQS.

El flujo general es:

```text
docker compose
       │
       ▼
  LocalStack
       │
       ▼
 scripts de inicialización
       │
       ▼
Recursos AWS locales
       │
       ▼
ms-orders / ms-tickets
```

Estos scripts forman parte del entorno de desarrollo local y no reemplazan la infraestructura AWS utilizada en un entorno real.

---

# 🔐 Seguridad

EventPass utiliza Spring Security y JWT para proteger las APIs.

El flujo general es:

```text
Cliente
   │
   │ Authorization: Bearer <JWT>
   ▼
Spring Security
   │
   ▼
JwtAuthenticationFilter
   │
   ├── Validación de firma
   ├── Validación de expiración
   ├── Validación de sesión
   ├── Validación de blacklist
   └── Extracción de authorities
   │
   ▼
Endpoint protegido
```

Los permisos se gestionan mediante RBAC.

### Roles principales

```text
ADMIN
STAFF
ORGANIZER
SUPPORT
CUSTOMER
```

---

# 📚 Documentación de las APIs

Cada microservicio incluye documentación mediante **OpenAPI / Swagger UI**.

### Swagger

| Servicio | Swagger UI |
|---|---|
| `ms-auth` | `http://localhost:8081/swagger-ui.html` |
| `ms-events` | `http://localhost:8082/swagger-ui.html` |
| `ms-orders` | `http://localhost:8083/swagger-ui.html` |
| `ms-tickets` | `http://localhost:8084/swagger-ui.html` |

### OpenAPI

Los documentos OpenAPI están disponibles mediante:

```text
/v3/api-docs
```

Por ejemplo:

```text
http://localhost:8081/v3/api-docs
```

---

# ❤️ Health Checks

Los microservicios exponen endpoints de salud mediante Spring Boot Actuator.

Ejemplo:

```text
http://localhost:8081/actuator/health
```

También están configurados health checks en Docker Compose para controlar la disponibilidad de las dependencias antes de iniciar los servicios que dependen de ellas.

---

# 🧪 Pruebas

Cada microservicio posee su propio proyecto Maven.

Para ejecutar las pruebas de un servicio:

```bash
cd ms-auth
./mvnw test
```

También se puede ejecutar:

```bash
cd ms-events
./mvnw test
```

```bash
cd ms-orders
./mvnw test
```

```bash
cd ms-tickets
./mvnw test
```

---

# 📁 Estructura del repositorio

```text
eventpass-backend/
│
├── ms-auth/
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
│
├── ms-events/
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
│
├── ms-orders/
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
│
├── ms-tickets/
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
│
├── scripts/
│   └── # Scripts de inicialización del entorno local
│
├── .github/
│   └── workflows/
│
├── docker-compose.yml
├── .env
├── .gitignore
└── README.md
```

Dentro de cada microservicio se mantiene una estructura orientada a responsabilidades:

```text
src/main/java/
└── cl/eventpass/
    └── ms-*/
        ├── controller/
        ├── service/
        ├── repository/
        ├── entity/
        ├── dto/
        ├── mapper/
        ├── security/
        ├── config/
        ├── exception/
        └── ...
```

---

# 🛠️ Stack tecnológico

| Tecnología | Uso |
|---|---|
| Java 21 | Lenguaje principal |
| Spring Boot | Framework de microservicios |
| Spring Security | Seguridad y autorización |
| JWT | Autenticación stateless |
| PostgreSQL | Persistencia de auth, eventos y órdenes |
| MongoDB | Persistencia de tickets |
| Redis | Sesiones y blacklist |
| AWS SQS | Comunicación asíncrona |
| LocalStack | Simulación local de AWS |
| Spring Data JPA | Acceso a PostgreSQL |
| Spring Data MongoDB | Acceso a MongoDB |
| Springdoc OpenAPI | Documentación de APIs |
| Spring Boot Actuator | Health checks y monitoreo |
| Docker | Contenerización |
| Docker Compose | Orquestación local |
| Maven | Gestión y construcción de proyectos |
| Lombok | Reducción de código repetitivo |

---

# 🔄 Flujo completo del sistema

El flujo principal de negocio puede resumirse de la siguiente manera:

```text
                 ┌─────────────┐
                 │   Cliente   │
                 └──────┬──────┘
                        │
                        ▼
                 ┌─────────────┐
                 │   ms-auth   │
                 └─────────────┘
                        │
                     JWT │
                        ▼
                 ┌─────────────┐
                 │  ms-events  │
                 └─────────────┘
                        │
                  Disponibilidad
                        │
                        ▼
                 ┌─────────────┐
                 │  ms-orders  │
                 └──────┬──────┘
                        │
                  Orden completada
                        │
                        ▼
                 ┌─────────────┐
                 │     SQS     │
                 └──────┬──────┘
                        │
                        ▼
                 ┌─────────────┐
                 │ ms-tickets  │
                 └──────┬──────┘
                        │
                        ▼
                     MongoDB
```

La separación permite que la emisión de tickets se procese de forma independiente de la operación de compra.

---

# 📌 Estado del proyecto

EventPass cuenta actualmente con:

- Arquitectura basada en microservicios.
- Separación de dominios.
- Database per Service.
- Autenticación y autorización mediante JWT.
- RBAC.
- Sesiones y blacklist mediante Redis.
- Gestión de eventos y recintos.
- Control de capacidad y concurrencia.
- Gestión de órdenes.
- Procesamiento de pagos.
- Comunicación asíncrona mediante SQS.
- Emisión y validación de tickets.
- PostgreSQL y MongoDB.
- Docker Compose.
- LocalStack para desarrollo local.
- Configuración diferenciada para desarrollo y producción.
- OpenAPI / Swagger.
- Health checks mediante Actuator.
- Configuración mediante variables de entorno.
