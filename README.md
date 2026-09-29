# 🎟️ EventPass - Arquitectura de emisión de tickets distribuida

**EventPass** es un sistema distribuido de alta concurrencia diseñado para gestionar la venta, reserva, emisión y validación de entradas para eventos masivos, priorizando el rendimiento y la escalabilidad durante picos de tráfico extremo.

La arquitectura se compone de microservicios desacoplados que aplican el patrón *Database per Service*, combinando comunicación síncrona vía REST APIs para consultas de bajo costo y procesamiento asíncrono basado en colas de mensajería.

## 🏛️ Principios de arquitectura

- **Desacoplamiento de dominios:** cada microservicio es dueño de sus datos y lógica de negocio.
- **Seguridad stateless:** autenticación y autorización mediante tokens JWT y RBAC.
- **Procesamiento asíncrono:** desacoplamiento entre la recepción de órdenes y la reserva de inventario.
- **Resiliencia y escalabilidad:** aislamiento de fallos para evitar caídas en cascada.

## 📦 Servicios de dominio

- **`ms-auth`**: gestión de identidades, perfiles de usuario y credenciales de acceso.
- **`ms-events`**: catálogo de eventos, fechas y actualización de aforo.
- **`ms-orders`**: orquestación de compras y trazabilidad de órdenes.
- **`ms-tickets`**: generación y validación de entradas mediante QR/Hash.

---

## 🔐 ms-auth: microservicio de autenticación

`ms-auth` centraliza la autenticación, autorización y administración de usuarios de EventPass. Actualmente está preparado para ejecutarse en un entorno local de desarrollo y evaluación, con una estructura que permite su posterior adaptación a un entorno cloud.

### ✨ Características principales

#### Autenticación

- Registro de usuarios con validación de email único.
- Login mediante email y contraseña.
- Contraseñas protegidas con `BCryptPasswordEncoder`.
- Access tokens JWT con duración configurable, actualmente de 24 horas.
- Refresh tokens con duración configurable, actualmente de 7 días.
- Renovación de access tokens mediante refresh token.
- Logout con revocación de sesión y blacklist del token en Redis.

#### Usuarios, roles y perfiles

- Roles diferenciados: `ADMIN`, `STAFF`, `ORGANIZER`, `SUPPORT` y `CLIENT`.
- Consulta y actualización del perfil propio.
- Administración de usuarios protegida mediante `@PreAuthorize("hasRole('ADMIN')")`.
- Creación de usuarios administrativos, staff, organizadores y soporte.
- Actualización de datos y estado de usuarios.
- Auditoría JPA para registrar creación y actualización de entidades.

#### Seguridad y sesiones

- Arquitectura stateless con `SessionCreationPolicy.STATELESS`.
- Autorización basada en roles (RBAC).
- Validación de firma, expiración, sesión activa y blacklist de cada JWT.
- Identificación de sesión mediante el claim `sid`.
- Identificación individual de tokens mediante `jti`.
- Sesiones y blacklist almacenadas en Redis con expiración automática.
- Respuestas estructuradas para errores de autenticación y autorización.

### 🏗️ Estructura del servicio

```
ms-auth/
├── src/main/java/cl/eventpass/ms_auth/
│   ├── controller/       # Endpoints REST de autenticación y administración
│   ├── service/          # Lógica de autenticación, JWT y sesiones
│   ├── security/         # Filtro y detalles de autenticación JWT
│   ├── entity/           # Entidades JPA
│   ├── dto/              # DTOs de requests y responses
│   ├── mapper/           # Conversión entre entidades y DTOs
│   ├── repository/       # Acceso a datos
│   ├── exception/        # Excepciones y manejo de errores
│   ├── enums/            # Roles y estados de usuario
│   └── config/           # Seguridad, auditoría, inicialización y OpenAPI
└── src/main/resources/
    ├── application.yaml
    ├── application-dev.yaml
    └── application-prod.yaml
```

## 🚀 Ejecución local

### Requisitos

- Java 21+
- Maven 3.8+
- PostgreSQL 14+
- Redis 6+

### Configuración

Desde el directorio del servicio:

```bash
cd ms-auth
cp .env.example .env
```

Luego ajusta las variables de entorno si es necesario y ejecuta:

```bash
./mvnw spring-boot:run
```

También es posible compilar y ejecutar el JAR:

```bash
./mvnw clean package
java -jar target/ms-auth-0.0.1-SNAPSHOT.jar
```

Desde la raíz del repositorio, Docker Compose permite levantar las dependencias locales:

```bash
docker-compose up -d
```

Endpoints útiles durante el desarrollo:

- API: `http://localhost:8081/api/v1`
- Swagger UI: `http://localhost:8081/swagger-ui.html`
- Health: `http://localhost:8081/actuator/health`

## 📋 Endpoints principales

### Autenticación

| Método | Endpoint | Descripción | Autenticación |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Registrar usuario | No |
| `POST` | `/api/v1/auth/login` | Iniciar sesión | No |
| `POST` | `/api/v1/auth/refresh` | Renovar access token | No |
| `POST` | `/api/v1/auth/logout` | Cerrar sesión | Bearer token |
| `GET` | `/api/v1/auth/me` | Obtener perfil actual | Bearer token |
| `PATCH` | `/api/v1/auth/me` | Actualizar perfil actual | Bearer token |

### Administración

Todos los endpoints administrativos requieren el rol `ADMIN`.

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/v1/admin/users` | Listar usuarios |
| `GET` | `/api/v1/admin/users/{id}` | Obtener usuario por ID |
| `PATCH` | `/api/v1/admin/users/{id}` | Actualizar usuario |
| `PATCH` | `/api/v1/admin/users/{id}/status` | Actualizar estado |
| `DELETE` | `/api/v1/admin/users/{id}` | Eliminar usuario |
| `POST` | `/api/v1/admin/users/admin` | Crear administrador |
| `POST` | `/api/v1/admin/users/staff` | Crear usuario staff |
| `POST` | `/api/v1/admin/users/organizer` | Crear organizador |
| `POST` | `/api/v1/admin/users/support` | Crear usuario de soporte |

## 🔄 Flujo de autenticación

1. El usuario se registra o inicia sesión.
2. `AuthService` valida las credenciales y crea una sesión en Redis.
3. `JwtService` genera un access token y un refresh token asociados al `sid` de la sesión.
4. El cliente envía el access token como `Authorization: Bearer <token>`.
5. `JwtAuthenticationFilter` valida la firma, expiración, sesión y blacklist antes de establecer el contexto de seguridad.
6. Al cerrar sesión, el access token se agrega a la blacklist y la sesión se revoca.
7. Cuando el access token expira, el cliente puede solicitar uno nuevo mediante el refresh token mientras la sesión siga activa.

### Estructura de los tokens

Los JWT incluyen, entre otros, los siguientes claims:

- `sub`: email del usuario.
- `sid`: identificador de sesión.
- `jti`: identificador único del token.
- `iat`: fecha de emisión.
- `exp`: fecha de expiración.
- `authorities`: roles del usuario en el access token.

## ⚙️ Configuración por ambientes

El perfil de desarrollo utiliza PostgreSQL local, `ddl-auto: update` y `show-sql: true`, lo que facilita el trabajo durante la evaluación local.

El perfil de producción ya contempla `ddl-auto: validate` y `show-sql: false`, dejando la aplicación preparada para una futura configuración cloud sin ejecutar cambios automáticos sobre el esquema.

Variables principales:

```
PORT=8081
DB_HOST=localhost
DB_PORT=5432
DB_NAME=eventpass_auth
DB_USER=postgres
DB_PASSWORD=postgrespassword
JWT_SECRET=<secret-base64>
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_SSL=false
SPRING_PROFILES_ACTIVE=dev
ADMIN_EMAIL=admin@eventpass.cl
ADMIN_PASSWORD=Cambiame123!
```

## 🧪 Pruebas

Para ejecutar las pruebas del servicio:

```bash
./mvnw test
```

El proyecto incluye dependencias para pruebas de Spring, seguridad, JPA y Testcontainers con PostgreSQL.

## 📚 Stack tecnológico

| Componente | Propósito |
|---|---|
| Java 21 | Lenguaje base |
| Spring Boot | Framework principal |
| Spring Security | Autenticación y autorización |
| JJWT 0.12.6 | Generación y validación de JWT |
| PostgreSQL | Persistencia de usuarios y credenciales |
| Redis | Sesiones y blacklist de tokens |
| Springdoc OpenAPI | Documentación de la API |
| Lombok | Reducción de código repetitivo |

## ☁️ Consideraciones para la evaluación 3

Para llevar el servicio a un entorno real en la nube, los principales ajustes previstos son:

- Configurar secretos mediante AWS Secrets Manager o Parameter Store.
- Utilizar PostgreSQL administrado mediante AWS RDS.
- Utilizar Redis administrado mediante ElastiCache.
- Configurar CORS explícitamente según el frontend.
- Incorporar rate limiting para login y registro.
- Agregar logging y monitoreo de eventos de seguridad.
- Configurar headers de seguridad y HTTPS.
- Revisar políticas de expiración, rotación y revocación de tokens.
- Evaluar MFA para roles administrativos.
- Incorporar health checks, métricas y alertas para CloudWatch.
