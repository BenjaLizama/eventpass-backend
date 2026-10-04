package cl.eventpass.ms_auth.e2e;

import cl.eventpass.ms_auth.MsAuthApplication;
import cl.eventpass.ms_auth.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = MsAuthApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(TestcontainersConfiguration.class)
class AuthFlowE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Test
    void getCurrentUser_WithoutToken_ReturnsUnauthorized()
            throws Exception {
        HttpResponse<String> response = sendGet(
                "/api/v1/auth/me",
                null
        );

        assertEquals(
                401,
                response.statusCode(),
                () -> "Respuesta inesperada: " + response.body()
        );
    }


    /* HAPPY PATH*/
    @Test
    void register_WithValidData_ReturnsCreatedAndTokens()
            throws Exception {
        String email =
                "e2e-" + System.currentTimeMillis() + "@test.com";

        String requestBody = """
                {
                  "email": "%s",
                  "password": "Password123!"
                }
                """.formatted(email);

        HttpResponse<String> response = sendPost(
                "/api/v1/auth/register",
                requestBody,
                null
        );

        assertEquals(
                201,
                response.statusCode(),
                () -> "Respuesta inesperada: " + response.body()
        );
        JsonNode root =
                objectMapper.readTree(response.body());

        assertEquals(201, root.get("status").asInt());

        JsonNode data = root.get("data");

        assertNotNull(data, "data no debe ser null");
    }

    @Test
    void register_ThenLogin_ReturnsTokens()
            throws Exception {
        String email =
                "e2e-login-" + System.currentTimeMillis() + "@test.com";
        String password = "Password123!";

        String registerBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        HttpResponse<String> registerResponse = sendPost(
                "/api/v1/auth/register",
                registerBody,
                null
        );

        assertEquals(
                201,
                registerResponse.statusCode(),
                () -> "Registro fallido: "
                        + registerResponse.body()
        );

        String loginBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        HttpResponse<String> loginResponse = sendPost(
                "/api/v1/auth/login",
                loginBody,
                null
        );
        assertEquals(
                200,
                loginResponse.statusCode(),
                () -> "Login fallido: "
                        + loginResponse.body()
        );

        JsonNode loginJson =
                objectMapper.readTree(loginResponse.body());

        assertEquals(200, loginJson.get("status").asInt());

        JsonNode data = loginJson.get("data");

        assertNotNull(data, "data no debe ser null");
    }

    @Test
    void register_ThenLogin_ThenGetCurrentUser_ReturnsUser()
            throws Exception {
        String email =
                "e2e-me-" + System.currentTimeMillis() + "@test.com";
        String password = "Password123!";

        String registerBody = """
            {
              "email": "%s",
              "password": "%s"
            }
            """.formatted(email, password);

        HttpResponse<String> registerResponse = sendPost(
                "/api/v1/auth/register",
                registerBody,
                null
        );

        assertEquals(
                201,
                registerResponse.statusCode(),
                () -> "Registro fallido: "
                        + registerResponse.body()
        );

        String loginBody = """
            {
              "email": "%s",
              "password": "%s"
            }
            """.formatted(email, password);

        HttpResponse<String> loginResponse = sendPost(
                "/api/v1/auth/login",
                loginBody,
                null
        );

        assertEquals(
                200,
                loginResponse.statusCode(),
                () -> "Login fallido: "
                        + loginResponse.body()
        );

        JsonNode loginJson =
                objectMapper.readTree(loginResponse.body());

        String accessToken = loginJson
                .get("data")
                .get("access_token")
                .asText();

        HttpResponse<String> meResponse = sendGet(
                "/api/v1/auth/me",
                accessToken
        );
        assertEquals(
                200,
                meResponse.statusCode(),
                () -> "Consulta de perfil fallida: "
                        + meResponse.body()
        );

        JsonNode meJson =
                objectMapper.readTree(meResponse.body());

        assertEquals(200, meJson.get("status").asInt());
        assertEquals(
                "Usuario obtenido exitosamente.",
                meJson.get("message").asText()
        );
        assertEquals(
                email,
                meJson.get("data").get("email").asText()
        );
    }

    @Test
    void register_ThenLogin_ThenRefresh_ReturnsNewAccessToken()
            throws Exception {
        String email =
                "e2e-refresh-" + System.currentTimeMillis() + "@test.com";
        String password = "Password123!";

        String registerBody = """
            {
              "email": "%s",
              "password": "%s"
            }
            """.formatted(email, password);

        HttpResponse<String> registerResponse = sendPost(
                "/api/v1/auth/register",
                registerBody,
                null
        );

        assertEquals(
                201,
                registerResponse.statusCode(),
                () -> "Registro fallido: "
                        + registerResponse.body()
        );

        String loginBody = """
            {
              "email": "%s",
              "password": "%s"
            }
            """.formatted(email, password);

        HttpResponse<String> loginResponse = sendPost(
                "/api/v1/auth/login",
                loginBody,
                null
        );

        assertEquals(
                200,
                loginResponse.statusCode(),
                () -> "Login fallido: "
                        + loginResponse.body()
        );

        JsonNode loginJson =
                objectMapper.readTree(loginResponse.body());

        JsonNode loginData = loginJson.get("data");

        String oldAccessToken =
                loginData.get("access_token").asText();
        String refreshToken =
                loginData.get("refresh_token").asText();

        String refreshBody = """
            {
              "refreshToken": "%s"
            }
            """.formatted(refreshToken);

        HttpResponse<String> refreshResponse = sendPost(
                "/api/v1/auth/refresh",
                refreshBody,
                null
        );
        assertEquals(
                200,
                refreshResponse.statusCode(),
                () -> "Refresh fallido: "
                        + refreshResponse.body()
        );

        JsonNode refreshJson =
                objectMapper.readTree(refreshResponse.body());

        assertEquals(200, refreshJson.get("status").asInt());
        assertEquals(
                "Token renovado exitosamente.",
                refreshJson.get("message").asText()
        );

        JsonNode refreshData = refreshJson.get("data");

        assertNotNull(refreshData, "data no debe ser null");
        assertTrue(
                refreshData.hasNonNull("access_token"),
                "Debe devolver access_token"
        );
        assertTrue(
                refreshData.hasNonNull("refresh_token"),
                "Debe devolver refresh_token"
        );
        assertEquals(
                "Bearer",
                refreshData.get("token_type").asText()
        );
        assertTrue(
                refreshData.get("expires_in").asLong() > 0,
                "expires_in debe ser positivo"
        );

        String newAccessToken =
                refreshData.get("access_token").asText();

        assertFalse(
                newAccessToken.isBlank(),
                "El nuevo access_token no debe estar vacío"
        );
        assertNotEquals(
                oldAccessToken,
                newAccessToken,
                "El refresh debe generar un nuevo access token"
        );
    }
    @Test
    void register_ThenLogin_ThenLogout_InvalidatesAccessToken()
            throws Exception {
        String email =
                "e2e-logout-" + System.currentTimeMillis() + "@test.com";
        String password = "Password123!";

        String registerBody = """
            {
              "email": "%s",
              "password": "%s"
            }
            """.formatted(email, password);

        HttpResponse<String> registerResponse = sendPost(
                "/api/v1/auth/register",
                registerBody,
                null
        );

        assertEquals(
                201,
                registerResponse.statusCode(),
                () -> "Registro fallido: "
                        + registerResponse.body()
        );

        String loginBody = """
            {
              "email": "%s",
              "password": "%s"
            }
            """.formatted(email, password);

        HttpResponse<String> loginResponse = sendPost(
                "/api/v1/auth/login",
                loginBody,
                null
        );

        assertEquals(
                200,
                loginResponse.statusCode(),
                () -> "Login fallido: "
                        + loginResponse.body()
        );

        JsonNode loginJson =
                objectMapper.readTree(loginResponse.body());

        String accessToken = loginJson
                .get("data")
                .get("access_token")
                .asText();

        HttpResponse<String> meBeforeLogout = sendGet(
                "/api/v1/auth/me",
                accessToken
        );

        assertEquals(
                200,
                meBeforeLogout.statusCode(),
                () -> "El token debería funcionar antes del logout: "
                        + meBeforeLogout.body()
        );

        HttpResponse<String> logoutResponse = sendPost(
                "/api/v1/auth/logout",
                "",
                accessToken
        );
        assertEquals(
                200,
                logoutResponse.statusCode(),
                () -> "Logout fallido: "
                        + logoutResponse.body()
        );

        JsonNode logoutJson =
                objectMapper.readTree(logoutResponse.body());

        assertEquals(200, logoutJson.get("status").asInt());
        assertEquals(
                "Sesión cerrada exitosamente.",
                logoutJson.get("message").asText()
        );

        HttpResponse<String> meAfterLogout = sendGet(
                "/api/v1/auth/me",
                accessToken
        );

        assertEquals(
                401,
                meAfterLogout.statusCode(),
                () -> "El token debería quedar invalidado después del logout: "
                        + meAfterLogout.body()
        );
    }

    /* Errors & Validation*/
    @Test
    void register_WithExistingEmail_ReturnsConflict()
            throws Exception {
        String email =
                "e2e-duplicate-" + System.currentTimeMillis() + "@test.com";
        String password = "Password123!";

        String firstRegisterBody = """
            {
              "email": "%s",
              "password": "%s"
            }
            """.formatted(email, password);

        HttpResponse<String> firstRegisterResponse = sendPost(
                "/api/v1/auth/register",
                firstRegisterBody,
                null
        );

        assertEquals(
                201,
                firstRegisterResponse.statusCode(),
                () -> "El primer registro debería funcionar: "
                        + firstRegisterResponse.body()
        );

        HttpResponse<String> secondRegisterResponse = sendPost(
                "/api/v1/auth/register",
                firstRegisterBody,
                null
        );

        assertEquals(
                409,
                secondRegisterResponse.statusCode(),
                () -> "Registro duplicado debería devolver 409: "
                        + secondRegisterResponse.body()
        );

        JsonNode responseJson = objectMapper.readTree(
                secondRegisterResponse.body()
        );

        assertEquals(
                409,
                responseJson.get("status").asInt()
        );
    }

    @Test
    void register_WithShortPassword_ReturnsBadRequest()
            throws Exception {
        String email =
                "e2e-short-" + System.currentTimeMillis() + "@test.com";

        String requestBody = """
            {
              "email": "%s",
              "password": "123"
            }
            """.formatted(email);

        HttpResponse<String> response = sendPost(
                "/api/v1/auth/register",
                requestBody,
                null
        );

        assertEquals(
                400,
                response.statusCode(),
                () -> "Contraseña corta debería devolver 400: "
                        + response.body()
        );

        JsonNode responseJson =
                objectMapper.readTree(response.body());

        assertEquals(
                400,
                responseJson.get("status").asInt()
        );
    }

    @Test
    void login_WithWrongPassword_ReturnsUnauthorized()
            throws Exception {
        String email =
                "e2e-wrong-pass-" + System.currentTimeMillis() + "@test.com";

        String registerBody = """
            {
              "email": "%s",
              "password": "Password123!"
            }
            """.formatted(email);

        HttpResponse<String> registerResponse = sendPost(
                "/api/v1/auth/register",
                registerBody,
                null
        );

        assertEquals(
                201,
                registerResponse.statusCode(),
                () -> "Registro fallido: "
                        + registerResponse.body()
        );

        String loginBody = """
            {
              "email": "%s",
              "password": "WrongPassword123!"
            }
            """.formatted(email);

        HttpResponse<String> loginResponse = sendPost(
                "/api/v1/auth/login",
                loginBody,
                null
        );

        assertEquals(
                401,
                loginResponse.statusCode(),
                () -> "Login con contraseña incorrecta debería devolver 401: "
                        + loginResponse.body()
        );

        JsonNode responseJson =
                objectMapper.readTree(loginResponse.body());

        assertEquals(
                401,
                responseJson.get("status").asInt()
        );
    }

    @Test
    void refresh_WithInvalidToken_ReturnsUnauthorized()
            throws Exception {
        String requestBody = """
            {
              "refreshToken": "invalid-refresh-token"
            }
            """;

        HttpResponse<String> response = sendPost(
                "/api/v1/auth/refresh",
                requestBody,
                null
        );

        assertEquals(
                401,
                response.statusCode(),
                () -> "Refresh inválido debería devolver 401: "
                        + response.body()
        );

        JsonNode responseJson =
                objectMapper.readTree(response.body());

        assertEquals(
                401,
                responseJson.get("status").asInt()
        );
    }

    @Test
    void logout_WithoutAuthorizationHeader_ReturnsUnauthorized()
            throws Exception {
        HttpResponse<String> response = sendPost(
                "/api/v1/auth/logout",
                "",
                null
        );

        assertEquals(
                401,
                response.statusCode(),
                () -> "Logout sin token debería devolver 401: "
                        + response.body()
        );

        JsonNode responseJson =
                objectMapper.readTree(response.body());

        assertEquals(
                401,
                responseJson.get("status").asInt()
        );
    }

    /* METODOS AUX  */
    private HttpResponse<String> sendGet(
            String path,
            String accessToken
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET();

        if (accessToken != null) {
            builder.header(
                    "Authorization",
                    "Bearer " + accessToken
            );
        }

        return httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> sendPost(
            String path,
            String body,
            String accessToken
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (accessToken != null) {
            builder.header(
                    "Authorization",
                    "Bearer " + accessToken
            );
        }

        return httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}