package cl.eventpass.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformHealthIT {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest(name = "{0} debe estar disponible")
    @CsvSource({
            "ms-auth, AUTH_BASE_URL, http://localhost:8081",
            "ms-events, EVENTS_BASE_URL, http://localhost:8082",
            "ms-orders, ORDERS_BASE_URL, http://localhost:8083",
            "ms-tickets, TICKETS_BASE_URL, http://localhost:8084"
    })
    @DisplayName("Comprobar disponibilidad de los cuatro microservicios")
    void shouldReportHealthy(
            String service,
            String environmentVariable,
            String defaultBaseUrl
    ) throws Exception {

        String configuredBaseUrl = System.getenv(environmentVariable);

        String baseUrl = configuredBaseUrl == null
                || configuredBaseUrl.isBlank()
                ? defaultBaseUrl
                : configuredBaseUrl;

        baseUrl = baseUrl.replaceAll("/+$", "");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/actuator/health"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(
                200,
                response.statusCode(),
                () -> service + " no está saludable. HTTP "
                        + response.statusCode()
                        + ". Respuesta: "
                        + response.body()
        );

        JsonNode body = objectMapper.readTree(response.body());

        assertEquals(
                "UP",
                body.path("status").asText(),
                () -> service + " no reporta UP. Respuesta: "
                        + response.body()
        );
    }
}