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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
                        "capacity", capacity + (asyncPurchase ? 1 : 0)
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
                        "ticketCategories", ticketCategories(capacity, asyncPurchase)
                ),
                201
        );

        String eventId = requiredText(event, "id");

        assertTrue(
                event.path("ticketCategories").isArray(),
                "El evento debe devolver sus categorías"
        );
        assertEquals(asyncPurchase ? 2 : 1, event.path("ticketCategories").size());

        String categoryId = requiredText(
                categoryId(event, "General"), "id"
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
        if (Boolean.parseBoolean(System.getenv("E2E_CHECK_TICKET_REPLAY"))) {
            JsonNode service = data("POST", auth, "/api/v1/auth/service-token", null, Map.of(
                    "clientId", requiredEnv("MS_ORDERS_CLIENT_ID"),
                    "clientSecret", requiredEnv("MS_ORDERS_CLIENT_SECRET")
            ), 200);
            String serviceToken = requiredText(service, "accessToken");
            assertEquals(204, request("POST", tickets, "/internal/tickets/issue", serviceToken,
                    purchasePayload(order)).statusCode());
            assertEquals(204, request("POST", tickets, "/internal/tickets/issue", serviceToken,
                    purchasePayload(order)).statusCode());
            assertEquals(ticketCodes(generated), ticketCodes(ticketsForOrder(customerToken, orderId)));
        }

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

            assertEquals(ticketCodes(generated), ticketCodes(ticketsForOrder(customerToken, orderId)));
            assertEquals("PAID", data("GET", orders, "/api/v1/orders/" + orderId,
                    customerToken, null, 200).path("status").asText());
            JsonNode refunded = data("GET", orders, path, secondToken, null, 200);
            assertEquals("REJECTED_NO_CAPACITY", refunded.path("status").asText());
            assertEquals("REFUNDED", refunded.path("paymentStatus").asText());
            assertTrue(ticketsForOrder(secondToken, secondId).isEmpty());
            assertEquals(0, category(eventId, categoryId).path("availableCapacity").asInt(-1));

            String lastSeatCategory = requiredText(categoryId(event, "Último cupo"), "id");
            verifyConcurrentPayments(eventId, lastSeatCategory, customerToken, secondToken);
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

        String usePath = "/api/v1/tickets/" + ticketCode + "/use";
        try (var executor = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<HttpResponse<String>> use = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return request("PATCH", tickets, usePath, staffToken, null);
            };
            var first = executor.submit(use);
            var second = executor.submit(use);
            var responses = List.of(first.get(25, TimeUnit.SECONDS), second.get(25, TimeUnit.SECONDS));
            assertEquals(List.of(200, 409), responses.stream().map(HttpResponse::statusCode).sorted().toList());
            String responseBody = responses.stream().filter(response -> response.statusCode() == 200)
                    .findFirst().orElseThrow().body();
            JsonNode usedTicket = mapper.readTree(responseBody).path("data");
            assertEquals("USED", usedTicket.path("status").asText());
            requiredText(usedTicket, "usedAt");
        }

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

    private List<Map<String, Object>> ticketCategories(int capacity, boolean asyncPurchase) {
        var general = Map.<String, Object>of("name", "General", "price", 10000,
                "totalCapacity", capacity, "maxPerUser", 4);
        return asyncPurchase ? List.of(general, Map.of("name", "Último cupo", "price", 10000,
                "totalCapacity", 1, "maxPerUser", 1)) : List.of(general);
    }

    private JsonNode categoryId(JsonNode event, String name) {
        for (JsonNode category : event.path("ticketCategories")) {
            if (name.equals(category.path("name").asText())) {
                return category;
            }
        }
        throw new AssertionError("Falta la localidad " + name);
    }

    private void verifyConcurrentPayments(String eventId, String categoryId, String first, String second)
            throws Exception {
        var body = Map.of("eventId", eventId, "ticketCategoryId", categoryId, "quantity", 1);
        String firstId = requiredText(data("POST", orders, "/api/v1/orders", first, body, 201), "id");
        String secondId = requiredText(data("POST", orders, "/api/v1/orders", second, body, 201), "id");
        assertEquals(1, category(eventId, categoryId).path("availableCapacity").asInt(-1));

        boolean interruptTickets = Boolean.parseBoolean(System.getenv("E2E_INTERRUPT_TICKETS"));
        boolean verifyDeadLetter = Boolean.parseBoolean(System.getenv("E2E_PURCHASE_DLQ"));
        String deadLetterUrl = verifyDeadLetter ? queueUrl("eventpass-purchase-errors") : null;
        assertFalse(verifyDeadLetter && !interruptTickets, "La prueba DLQ requiere E2E_INTERRUPT_TICKETS");
        if (verifyDeadLetter) {
            assertEquals(0, queueCount(deadLetterUrl, "ApproximateNumberOfMessages"));
            assertEquals(0, queueCount(deadLetterUrl, "ApproximateNumberOfMessagesNotVisible"));
        }
        if (interruptTickets) {
            ticketsContainer("stop");
        }
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CyclicBarrier(2);
            var firstPayment = executor.submit(() -> payTogether(firstId, first, start));
            var secondPayment = executor.submit(() -> payTogether(secondId, second, start));
            assertEquals("PENDING_ISSUANCE", firstPayment.get(25, TimeUnit.SECONDS));
            assertEquals("PENDING_ISSUANCE", secondPayment.get(25, TimeUnit.SECONDS));
            if (interruptTickets) {
                var pending = List.of("PENDING_ISSUANCE", "REJECTED_NO_CAPACITY");
                await().alias("Aforo reservado con Tickets detenido").atMost(Duration.ofSeconds(60))
                        .untilAsserted(() -> {
                            assertEquals(0, category(eventId, categoryId).path("availableCapacity").asInt(-1));
                            var states = List.of(orderStatus(firstId, first), orderStatus(secondId, second));
                            assertFalse(states.contains("PAID"));
                            assertTrue(states.contains("PENDING_ISSUANCE"));
                            assertTrue(pending.containsAll(states));
                        });
            }
            if (verifyDeadLetter) {
                await().alias("Compra enviada a la DLQ").atMost(Duration.ofSeconds(90))
                        .untilAsserted(() -> assertEquals(1, queueCount(deadLetterUrl, "ApproximateNumberOfMessages")));
                JsonNode message = sqs("ReceiveMessage", Map.of("QueueUrl", deadLetterUrl,
                        "MaxNumberOfMessages", 1, "VisibilityTimeout", 0)).path("Messages").get(0);
                assertNotNull(message);
                String queuedId = mapper.readTree(requiredText(message, "Body")).path("orderId").asText();
                assertTrue(List.of(firstId, secondId).contains(queuedId));
                assertEquals("PENDING_ISSUANCE", orderStatus(queuedId, queuedId.equals(firstId) ? first : second));
            }
        } finally {
            if (interruptTickets) {
                ticketsContainer("start");
            }
        }

        if (verifyDeadLetter) {
            await().alias("Tickets disponible").atMost(Duration.ofSeconds(90))
                    .pollInterval(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertEquals(200,
                            request("GET", tickets, "/api/v1/tickets/me", first, null).statusCode()));
            requiredText(sqs("StartMessageMoveTask", Map.of(
                    "SourceArn", queueArn(deadLetterUrl), "DestinationArn", queueArn(purchaseQueueUrl()),
                    "MaxNumberOfMessagesPerSecond", 1
            )), "TaskHandle");
        }

        await().alias("Emisión y devolución por último cupo").atMost(Duration.ofSeconds(240))
                .untilAsserted(() -> {
                    boolean firstWon = orderStatus(firstId, first).equals("PAID");
                    String winnerId = firstWon ? firstId : secondId;
                    String winnerToken = firstWon ? first : second;
                    String loserId = firstWon ? secondId : firstId;
                    String loserToken = firstWon ? second : first;
                    assertEquals("PAID", orderStatus(winnerId, winnerToken));
                    JsonNode loser = data("GET", orders, "/api/v1/orders/" + loserId, loserToken, null, 200);
                    assertEquals("REJECTED_NO_CAPACITY", loser.path("status").asText());
                    assertEquals("REFUNDED", loser.path("paymentStatus").asText());
                    assertEquals(1, ticketsForOrder(winnerToken, winnerId).size());
                    assertTrue(ticketsForOrder(loserToken, loserId).isEmpty());
                    assertEquals(0, category(eventId, categoryId).path("availableCapacity").asInt(-1));
                    if (verifyDeadLetter) {
                        assertEquals(0, queueCount(deadLetterUrl, "ApproximateNumberOfMessages"));
                    }
                });
    }

    private String payTogether(String id, String token, CyclicBarrier start) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        return data("POST", orders, "/api/v1/orders/" + id + "/payment", token,
                Map.of("paymentMethod", "CARD"), 200).path("status").asText();
    }

    private String orderStatus(String id, String token) throws Exception {
        return data("GET", orders, "/api/v1/orders/" + id, token, null, 200).path("status").asText();
    }

    private void ticketsContainer(String action) throws Exception {
        var process = new ProcessBuilder("docker", action, "eventpass-backend-ms-tickets-1")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (!process.waitFor(45, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Docker no terminó: " + action);
        }
        assertEquals(0, process.exitValue(), "Docker no pudo " + action + " Tickets");
    }

    private String purchaseQueueUrl() throws Exception {
        return queueUrl("eventpass-purchase-pending");
    }

    private String queueUrl(String name) throws Exception {
        return requiredText(sqs("GetQueueUrl", Map.of("QueueName", name)), "QueueUrl");
    }

    private int queueCount(String url, String attribute) throws Exception {
        return sqs("GetQueueAttributes", Map.of("QueueUrl", url, "AttributeNames", List.of(attribute)))
                .path("Attributes").path(attribute).asInt(-1);
    }

    private String queueArn(String url) throws Exception {
        return requiredText(sqs("GetQueueAttributes", Map.of(
                "QueueUrl", url, "AttributeNames", List.of("QueueArn"))).path("Attributes"), "QueueArn");
    }

    private void replayPurchase(JsonNode order) throws Exception {
        var payload = new HashMap<>(purchasePayload(order));
        payload.put("pendingIssuance", true);
        String message = mapper.writeValueAsString(payload);
        requiredText(sqs("SendMessage", Map.of("QueueUrl", purchaseQueueUrl(), "MessageBody", message)), "MessageId");
    }

    private List<String> ticketCodes(List<JsonNode> tickets) {
        return tickets.stream().map(ticket -> ticket.path("ticketCode").asText()).sorted().toList();
    }

    private Map<String, Object> purchasePayload(JsonNode order) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode item : order.path("items")) {
            items.add(Map.of(
                    "orderItemId", requiredText(item, "id"),
                    "eventId", requiredText(item, "eventId"),
                    "ticketCategoryId", requiredText(item, "ticketCategoryId"),
                    "quantity", item.path("quantity").asInt()
            ));
        }
        return Map.of(
                "orderId", requiredText(order, "id"),
                "userId", requiredText(order, "userId"),
                "items", items
        );
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
