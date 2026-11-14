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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class EventPassPurchaseFlowIT {

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
    @DisplayName("Compra completa: Auth → Events → Orders → SQS → Tickets")
    void purchaseGeneratesTicketsAndPreventsReuse() throws Exception {

        String runId = UUID.randomUUID().toString();
        String password = "Password123!";
        int quantity = 2;
        boolean asyncPurchase = Boolean.parseBoolean(System.getenv("E2E_ASYNC_PURCHASE"));
        int capacity = asyncPurchase ? quantity : 10;

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
                        "name", "Recinto E2E " + runId,
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
                        "title", "Evento E2E " + runId,
                        "description", "Evento creado por prueba automatizada",
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

        JsonNode initialCategory = category(eventId, categoryId);
        assertEquals(
                capacity,
                initialCategory.path("availableCapacity").asInt(-1)
        );

        String customerEmail = "customer-" + runId + "@test.com";

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

        JsonNode customer = data(
                "GET",
                auth,
                "/api/v1/auth/me",
                customerToken,
                null,
                200
        );

        String customerId = requiredText(customer, "id");

        JsonNode order = data(
                "POST",
                orders,
                "/api/v1/orders",
                customerToken,
                Map.of(
                        "eventId", eventId,
                        "ticketCategoryId", categoryId,
                        "quantity", quantity
                ),
                201
        );

        String orderId = requiredText(order, "id");

        assertEquals(customerId, order.path("userId").asText());
        assertEquals(asyncPurchase ? "AWAITING_PAYMENT" : "PENDING", order.path("status").asText());
        assertEquals("PENDING", order.path("paymentStatus").asText());

        assertEquals(
                0,
                new BigDecimal("20000").compareTo(
                        new BigDecimal(
                                requiredText(order, "totalAmount")
                        )
                ),
                "El total debe ser precio × cantidad"
        );

        assertEquals(
                asyncPurchase ? capacity : capacity - quantity,
                category(eventId, categoryId)
                        .path("availableCapacity").asInt(-1),
                "Aforo después de crear la orden"
        );

        JsonNode paidOrder = data(
                "POST",
                orders,
                "/api/v1/orders/" + orderId + "/payment",
                customerToken,
                Map.of("paymentMethod", "CARD"),
                200
        );

        assertEquals(
                asyncPurchase ? "PENDING_ISSUANCE" : "PAID",
                paidOrder.path("status").asText(),
                "El entorno debe tener el pago simulado en APPROVED"
        );
        assertEquals(
                "APPROVED",
                paidOrder.path("paymentStatus").asText()
        );

        await()
                .alias("Emisión de tickets para la orden " + orderId)
                .atMost(Duration.ofSeconds(asyncPurchase ? 150 : 30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<JsonNode> generated = ticketsForOrder(
                            customerToken, orderId
                    );

                    assertEquals(quantity, generated.size());
                    if (asyncPurchase) {
                        assertEquals("PAID", data("GET", orders, "/api/v1/orders/" + orderId,
                                customerToken, null, 200).path("status").asText());
                    }

                    for (JsonNode ticket : generated) {
                        assertEquals(
                                customerId,
                                ticket.path("userId").asText()
                        );
                        assertEquals(
                                eventId,
                                ticket.path("eventId").asText()
                        );
                        assertEquals(
                                categoryId,
                                ticket.path("ticketCategoryId").asText()
                        );
                        assertEquals(
                                "ACTIVE",
                                ticket.path("status").asText()
                        );
                        assertFalse(
                                requiredText(ticket, "ticketCode").isBlank()
                        );
                    }

                    assertEquals(
                            quantity,
                            generated.stream()
                                    .map(t -> t.path("ticketCode").asText())
                                    .distinct()
                                    .count()
                    );
                });

        List<JsonNode> generated = ticketsForOrder(
                customerToken, orderId
        );

        if (asyncPurchase) {
            String secondEmail = "segundo-" + runId + "@test.com";
            data("POST", auth, "/api/v1/auth/register", null,
                    Map.of("email", secondEmail, "password", password), 201);
            String secondToken = login(secondEmail, password);
            JsonNode second = data("POST", orders, "/api/v1/orders", secondToken,
                    Map.of("eventId", eventId, "ticketCategoryId", categoryId, "quantity", 1), 201);
            String secondId = requiredText(second, "id");
            String path = "/api/v1/orders/" + secondId;
            assertEquals("PENDING_ISSUANCE", data("POST", orders, path + "/payment", secondToken,
                    Map.of("paymentMethod", "CARD"), 200).path("status").asText());
            await().atMost(Duration.ofSeconds(150)).untilAsserted(() -> {
                JsonNode rejected = data("GET", orders, path, secondToken, null, 200);
                assertEquals("REJECTED_NO_CAPACITY", rejected.path("status").asText());
                assertEquals("REFUNDED", rejected.path("paymentStatus").asText());
                assertTrue(ticketsForOrder(secondToken, secondId).isEmpty());
                assertEquals(0, category(eventId, categoryId).path("availableCapacity").asInt(-1));
            });

            replayPurchase(order);
            replayPurchase(second);
            await().alias("Consumo de las compras reenviadas")
                    .atMost(Duration.ofSeconds(150))
                    .untilAsserted(() -> {
                        JsonNode attributes = sqs("GetQueueAttributes", Map.of(
                                "QueueUrl", purchaseQueueUrl(),
                                "AttributeNames", List.of("ApproximateNumberOfMessages",
                                        "ApproximateNumberOfMessagesNotVisible")
                        )).path("Attributes");
                        assertEquals(0, attributes.path("ApproximateNumberOfMessages").asInt(-1));
                        assertEquals(0, attributes.path("ApproximateNumberOfMessagesNotVisible").asInt(-1));
                    });

            assertEquals(generated.stream().map(ticket -> ticket.path("ticketCode").asText()).sorted().toList(),
                    ticketsForOrder(customerToken, orderId).stream()
                            .map(ticket -> ticket.path("ticketCode").asText()).sorted().toList());
            assertEquals("PAID", data("GET", orders, "/api/v1/orders/" + orderId,
                    customerToken, null, 200).path("status").asText());
            JsonNode refunded = data("GET", orders, path, secondToken, null, 200);
            assertEquals("REJECTED_NO_CAPACITY", refunded.path("status").asText());
            assertEquals("REFUNDED", refunded.path("paymentStatus").asText());
            assertTrue(ticketsForOrder(secondToken, secondId).isEmpty());
            assertEquals(0, category(eventId, categoryId).path("availableCapacity").asInt(-1));
        }

        String ticketCode = requiredText(
                generated.get(0), "ticketCode"
        );

        String staffEmail = "staff-" + runId + "@test.com";

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

        String staffToken = login(staffEmail, password);

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

        HttpResponse<String> secondUse = request(
                "PATCH",
                tickets,
                "/api/v1/tickets/" + ticketCode + "/use",
                staffToken,
                null
        );

        assertEquals(
                409,
                secondUse.statusCode(),
                "El segundo uso del ticket debe rechazarse"
        );

        JsonNode persistedTicket = data(
                "GET",
                tickets,
                "/api/v1/tickets/" + ticketCode,
                customerToken,
                null,
                200
        );

        assertEquals("USED", persistedTicket.path("status").asText());

        assertEquals(
                capacity - quantity,
                category(eventId, categoryId)
                        .path("availableCapacity").asInt(-1),
                "El pago no debe descontar nuevamente la capacidad"
        );
    }

    private String purchaseQueueUrl() throws Exception {
        return requiredText(sqs("GetQueueUrl", Map.of("QueueName", "eventpass-purchase-pending")), "QueueUrl");
    }

    private void replayPurchase(JsonNode order) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode item : order.path("items")) {
            items.add(Map.of(
                    "orderItemId", requiredText(item, "id"),
                    "eventId", requiredText(item, "eventId"),
                    "ticketCategoryId", requiredText(item, "ticketCategoryId"),
                    "quantity", item.path("quantity").asInt()
            ));
        }
        String message = mapper.writeValueAsString(Map.of(
                "orderId", requiredText(order, "id"),
                "userId", requiredText(order, "userId"),
                "items", items,
                "pendingIssuance", true
        ));
        requiredText(sqs("SendMessage", Map.of("QueueUrl", purchaseQueueUrl(), "MessageBody", message)), "MessageId");
    }

    private JsonNode sqs(String action, Map<String, ?> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:4567"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-amz-json-1.0")
                .header("X-Amz-Target", "AmazonSQS." + action)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response::body);
        return mapper.readTree(response.body());
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
