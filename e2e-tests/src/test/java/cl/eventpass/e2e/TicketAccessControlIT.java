package cl.eventpass.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class TicketAccessControlIT {

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

    private final String tickets = baseUrl(
            "TICKETS_BASE_URL", "http://localhost:8084"
    );

    @Test
    @DisplayName("Control de acceso a tickets: Auth → Events → Orders → SQS → Tickets")
    void shouldProtectTicketAccessAndUsage() throws Exception {

        String runId = UUID.randomUUID().toString();
        String password = "Password123!";
        int quantity = 1;
        int capacity = 10;

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
                        "name", "Recinto Acceso E2E " + runId,
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
                        "title", "Evento Acceso E2E " + runId,
                        "description", "Evento creado para probar acceso a tickets",
                        "category", "CONCERT",
                        "startDate", Instant.now()
                                .plus(7, ChronoUnit.DAYS).toString(),
                        "endDate", Instant.now()
                                .plus(7, ChronoUnit.DAYS)
                                .plus(2, ChronoUnit.HOURS).toString(),
                        "venueId", venueId,
                        "ticketCategories", List.of(Map.of(
                                "name", "General",
                                "price", 10000,
                                "totalCapacity", capacity,
                                "maxPerUser", 4
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

        String ownerEmail = "ticket-owner-" + runId + "@test.com";
        String attackerEmail = "ticket-attacker-" + runId + "@test.com";
        String staffEmail = "ticket-staff-" + runId + "@test.com";

        data(
                "POST",
                auth,
                "/api/v1/auth/register",
                null,
                Map.of(
                        "email", ownerEmail,
                        "password", password
                ),
                201
        );

        data(
                "POST",
                auth,
                "/api/v1/auth/register",
                null,
                Map.of(
                        "email", attackerEmail,
                        "password", password
                ),
                201
        );

        data(
                "POST",
                auth,
                "/api/v1/admin/users/staff",
                adminToken,
                Map.of(
                        "email", staffEmail,
                        "password", password
                ),
                201
        );

        String ownerToken = login(ownerEmail, password);
        String attackerToken = login(attackerEmail, password);
        String staffToken = login(staffEmail, password);

        JsonNode owner = data(
                "GET",
                auth,
                "/api/v1/auth/me",
                ownerToken,
                null,
                200
        );

        String ownerId = requiredText(owner, "id");

        JsonNode order = data(
                "POST",
                orders,
                "/api/v1/orders",
                ownerToken,
                Map.of(
                        "eventId", eventId,
                        "ticketCategoryId", categoryId,
                        "quantity", quantity
                ),
                201
        );

        String orderId = requiredText(order, "id");

        data(
                "POST",
                orders,
                "/api/v1/orders/" + orderId + "/payment",
                ownerToken,
                Map.of("paymentMethod", "CARD"),
                200
        );

        await()
                .alias("Emisión de tickets para la orden " + orderId)
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<JsonNode> generated = ticketsForOrder(
                            ownerToken, orderId
                    );
                    assertEquals(quantity, generated.size());
                });

        List<JsonNode> generated = ticketsForOrder(
                ownerToken, orderId
        );

        String ticketCode = requiredText(
                generated.get(0), "ticketCode"
        );

        HttpResponse<String> attackerGetTicket = request(
                "GET",
                tickets,
                "/api/v1/tickets/" + ticketCode,
                attackerToken,
                null
        );

        assertEquals(
                404,
                attackerGetTicket.statusCode(),
                "Un cliente no debe poder consultar el ticket de otro usuario"
        );

        HttpResponse<String> attackerUseTicket = request(
                "PATCH",
                tickets,
                "/api/v1/tickets/" + ticketCode + "/use",
                attackerToken,
                null
        );

        assertEquals(
                403,
                attackerUseTicket.statusCode(),
                "Un cliente no debe poder usar un ticket"
        );

        HttpResponse<String> ownerUseTicket = request(
                "PATCH",
                tickets,
                "/api/v1/tickets/" + ticketCode + "/use",
                ownerToken,
                null
        );

        assertEquals(
                403,
                ownerUseTicket.statusCode(),
                "El dueño del ticket no debe poder usarlo; solo STAFF"
        );

        JsonNode usedTicket = data(
                "PATCH",
                tickets,
                "/api/v1/tickets/" + ticketCode + "/use",
                staffToken,
                null,
                200
        );

        assertEquals("USED", usedTicket.path("status").asText());
        assertFalse(usedTicket.path("usedAt").isMissingNode());
        assertFalse(usedTicket.path("usedAt").isNull());

        JsonNode persistedTicket = data(
                "GET",
                tickets,
                "/api/v1/tickets/" + ticketCode,
                ownerToken,
                null,
                200
        );

        assertEquals("USED", persistedTicket.path("status").asText());
    }

    private List<JsonNode> ticketsForOrder(
            String token,
            String orderId
    ) throws Exception {

        JsonNode response = data(
                "GET",
                tickets,
                "/api/v1/tickets/me",
                token,
                null,
                200
        );

        assertTrue(response.isArray(), "Se esperaba una lista de tickets");

        List<JsonNode> result = new ArrayList<>();

        for (JsonNode ticket : response) {
            if (orderId.equals(ticket.path("orderId").asText())) {
                result.add(ticket);
            }
        }

        return result;
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