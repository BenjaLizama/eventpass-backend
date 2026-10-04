## 🧪 Pruebas

Para ejecutar todas las pruebas del microservicio:

```bash
./mvnw clean test
```

En Windows con Maven Wrapper:

```CMD
.\mvnw.cmd clean test
```

Las pruebas de integración y E2E utilizan Testcontainers, por lo que requieren Docker Desktop en ejecución.

### Cobertura de pruebas

El proyecto cubre tres niveles de prueba:

| Nivel | Objetivo | Herramientas principales |
|---|---|---|
| Unitarias | Validar la lógica de servicios y componentes aislados | JUnit 5, Mockito |
| Integración | Validar el acceso a datos y la interacción con PostgreSQL real | JUnit 5, Spring Data JPA, Testcontainers |
| E2E | Validar flujos HTTP completos contra la aplicación levantada | JUnit 5, `@SpringBootTest`, `HttpClient`, Testcontainers |

### Pruebas unitarias

Las pruebas unitarias validan componentes individuales sin levantar la aplicación completa ni depender de una base de datos real.

`AuthServiceTest` cubre la lógica principal de `AuthService` mediante mocks:

- Consulta del usuario actual cuando existe.
- Error cuando el usuario no existe.
- Registro exitoso: codificación de contraseña, creación de credenciales, generación de tokens y creación de sesión.
- Registro con email duplicado: se lanza `EmailAlreadyExistsException` y no se interactúa con el encoder ni el mapper.

`JwtServiceTest` valida la generación y validación de tokens JWT:

- Generación de access token con usuario, sesión, `jti`, expiración y authorities.
- Generación de refresh token con usuario y sesión.
- Validez de un token activo cuando no está en blacklist y la sesión está activa.
- Rechazo de tokens incluidos en la blacklist.
- Rechazo de tokens asociados a sesiones inactivas.
- Detección de tokens expirados.

### Pruebas de integración

`CredentialRepositoryIntegrationTest` valida el repositorio `CredentialRepository` contra una base de datos PostgreSQL real proporcionada por Testcontainers.

Cubre:

- Persistencia y recuperación de credenciales por ID.
- Búsqueda de usuarios activos mediante `findByEmailAndDeletedAtIsNull`.
- Exclusión de usuarios eliminados lógicamente.
- Búsqueda de usuarios eliminados mediante `findByEmail`.
- Verificación de existencia por rol.
- Restricción de unicidad del email mediante una excepción de integridad de datos.

Estas pruebas usan un contenedor PostgreSQL `postgres:16-alpine` y configuran dinámicamente la conexión, el usuario y la contraseña de la base de datos.

### Pruebas E2E

`AuthFlowE2ETest` levanta la aplicación completa en un puerto aleatorio y ejecuta peticiones HTTP reales. Utiliza contenedores reales de PostgreSQL y Redis mediante `TestcontainersConfiguration`.

Cubre el flujo de autenticación y sus errores:

- Acceso a `/api/v1/auth/me` sin token: responde `401 Unauthorized`.
- Registro válido: responde `201 Created`.
- Registro y login: responde `200 OK`.
- Registro, login y consulta de perfil: `/me` devuelve los datos del usuario autenticado.
- Refresh token: genera un nuevo `access_token`.
- Logout: revoca la sesión y el access token deja de ser válido.
- Registro con email duplicado: responde `409 Conflict`.
- Registro con contraseña demasiado corta: responde `400 Bad Request`.
- Login con contraseña incorrecta: responde `401 Unauthorized`.
- Refresh con token inválido: responde `401 Unauthorized`.
- Logout sin cabecera `Authorization`: responde `401 Unauthorized`.

Las respuestas de autenticación incluyen `access_token`, `refresh_token`, `token_type` y `expires_in`.