package cl.eventpass.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderPerUserLimitIT {

    private final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String auth = baseUrl(
            "AUTH_BASE_URL", "http://localhost:8081"
    );

    private final String events = baseUrl(
            "EVENTS_BASE_URL", "http://localhost:8082"
    );

    private final String orders = baseUrl(
            "ORDERS_BASE_URL", "http://localhost:8083"
    );

    @Test
    @DisplayName("Límite por usuario: Auth → Events → Orders")
    void shouldRejectOrderAboveMaxPerUser() throws Exception {

        String runId = UUID.randomUUID().toString();
        String password = "Password123!";
        int capacity = 10;
        int maxPerUser = 2;
        int price = 10000;

        String adminToken = login(
                requiredEnv("E2E_ADMIN_EMAIL"),
                requiredEnv("E2E_ADMIN_PASSWORD")
        );

        JsonNode venue = data(
                "POST",
                events,
                "/api/v1/venues",
                adminToken,
                Map.of(
                        "name", "Recinto Límite E2E " + runId,
                        "address", "Calle de pruebas 123",
                        "city", "Santiago",
                        "country", "Chile",
                        "capacity", capacity
                ),
                201
        );

        String venueId = requiredText(venue, "id");

        JsonNode event = data(
                "POST",
                events,
                "/api/v1/events",
                adminToken,
                Map.of(
                        "title", "Evento Límite E2E " + runId,
                        "description", "Evento creado para probar el límite por usuario",
                        "category", "CONCERT",
                        "startDate", Instant.now()
                                .plus(7, ChronoUnit.DAYS).toString(),
                        "endDate", Instant.now()
                                .plus(7, ChronoUnit.DAYS)
                                .plus(2, ChronoUnit.HOURS).toString(),
                        "venueId", venueId,
                        "ticketCategories", List.of(Map.of(
                                "name", "General",
                                "price", price,
                                "totalCapacity", capacity,
                                "maxPerUser", maxPerUser
                        ))
                ),
                201
        );

        String eventId = requiredText(event, "id");

        assertTrue(
                event.path("ticketCategories").isArray(),
                "El evento debe devolver sus categorías"
        );
        assertEquals(1, event.path("ticketCategories").size());

        String categoryId = requiredText(
                event.path("ticketCategories").get(0), "id"
        );

        JsonNode published = data(
                "PATCH",
                events,
                "/api/v1/events/" + eventId + "/publish",
                adminToken,
                null,
                200
        );

        assertEquals("PUBLISHED", published.path("status").asText());

        JsonNode initialCategory = category(eventId, categoryId);
        assertEquals(
                capacity,
                initialCategory.path("availableCapacity").asInt(-1)
        );

        String customerEmail = "limit-customer-" + runId + "@test.com";

        data(
                "POST",
                auth,
                "/api/v1/auth/register",
                null,
                Map.of(
                        "email", customerEmail,
                        "password", password
                ),
                201
        );

        String customerToken = login(customerEmail, password);

        JsonNode firstOrder = data(
                "POST",
                orders,
                "/api/v1/orders",
                customerToken,
                Map.of(
                        "eventId", eventId,
                        "ticketCategoryId", categoryId,
                        "quantity", maxPerUser
                ),
                201
        );

        String firstOrderId = requiredText(firstOrder, "id");

        assertEquals("PENDING", firstOrder.path("status").asText());
        assertEquals(
                capacity - maxPerUser,
                category(eventId, categoryId)
                        .path("availableCapacity").asInt(-1),
                "La primera orden debe reservar el máximo permitido"
        );

        HttpResponse<String> secondOrder = request(
                "POST",
                orders,
                "/api/v1/orders",
                customerToken,
                Map.of(
                        "eventId", eventId,
                        "ticketCategoryId", categoryId,
                        "quantity", 1
                )
        );

        assertTrue(
                secondOrder.statusCode() >= 400,
                "Comprar más entradas que maxPerUser debe rechazarse, pero devolvió "
                        + secondOrder.statusCode()
        );

        assertEquals(
                capacity - maxPerUser,
                category(eventId, categoryId)
                        .path("availableCapacity").asInt(-1),
                "El rechazo no debe alterar el aforo"
        );

        JsonNode persistedFirstOrder = data(
                "GET",
                orders,
                "/api/v1/orders/" + firstOrderId,
                customerToken,
                null,
                200
        );

        assertEquals(
                "PENDING",
                persistedFirstOrder.path("status").asText(),
                "La primera orden debe seguir pendiente"
        );
    }

    private JsonNode category(
            String eventId,
            String categoryId
    ) throws Exception {
        return data(
                "GET",
                events,
                "/api/v1/events/" + eventId
                        + "/ticket-categories/" + categoryId,
                null,
                null,
                200
        );
    }

    private String login(
            String email,
            String password
    ) throws Exception {

        JsonNode response = data(
                "POST",
                auth,
                "/api/v1/auth/login",
                null,
                Map.of("email", email, "password", password),
                200
        );

        return requiredText(response, "access_token");
    }

    private JsonNode data(
            String method,
            String baseUrl,
            String path,
            String token,
            Object body,
            int expectedStatus
    ) throws Exception {

        HttpResponse<String> response = request(
                method, baseUrl, path, token, body
        );

        if (response.statusCode() != expectedStatus) {
            JsonNode error = mapper.readTree(response.body());

            System.err.println(
                    method + " " + path
                            + " → HTTP " + response.statusCode()
                            + "\ncode: " + error.path("code").asText()
                            + "\nmessage: " + error.path("message").asText()
                            + "\ndeveloperMessage: "
                            + error.path("developerMessage").asText()
            );
        }

        assertEquals(
                expectedStatus,
                response.statusCode(),
                () -> method + " " + path
                        + " esperaba HTTP " + expectedStatus
                        + " pero devolvió " + response.statusCode()
                        + ". Revisa los logs del servicio."
        );

        JsonNode root = mapper.readTree(response.body());
        JsonNode result = root.get("data");

        assertNotNull(result, method + " " + path + " no devolvió data");
        assertFalse(result.isNull(), method + " " + path + " devolvió data null");

        return result;
    }

    private HttpResponse<String> request(
            String method,
            String baseUrl,
            String path,
            String token,
            Object body
    ) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpRequest.BodyPublisher publisher =
                HttpRequest.BodyPublishers.noBody();

        if (body != null) {
            builder.header("Content-Type", "application/json");
            publisher = HttpRequest.BodyPublishers.ofString(
                    mapper.writeValueAsString(body)
            );
        }

        return client.send(
                builder.method(method, publisher).build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static String requiredText(
            JsonNode node,
            String field
    ) {
        JsonNode value = node.get(field);
        assertNotNull(value, "Falta el campo " + field);
        assertFalse(value.isNull(), "El campo " + field + " es null");
        String text = value.asText();
        assertFalse(text.isBlank(), "El campo " + field + " está vacío");
        return text;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Debes configurar la variable " + name
            );
        }

        return value;
    }

    private static String baseUrl(
            String variable,
            String defaultValue
    ) {
        String value = System.getenv(variable);

        return (value == null || value.isBlank()
                ? defaultValue
                : value).replaceAll("/+$", "");
    }
}