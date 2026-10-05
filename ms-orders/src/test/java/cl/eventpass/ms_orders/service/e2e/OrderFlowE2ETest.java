package cl.eventpass.ms_orders.service.e2e;

import cl.eventpass.ms_orders.dto.response.OrderResponse;
import cl.eventpass.ms_orders.dto.response.StandardResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import static org.junit.jupiter.api.Assertions.assertTrue;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
class OrderFlowE2ETest {

    private static final String JWT_SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250655368566D5971";

    @LocalServerPort
    private int port;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:16-alpine")
            );

    static MockWebServer authMockServer;
    static MockWebServer eventsMockServer;

    @BeforeAll
    static void startMockServers() throws IOException {
        authMockServer = new MockWebServer();
        authMockServer.start();

        eventsMockServer = new MockWebServer();
        eventsMockServer.start();
    }

    @AfterAll
    static void stopMockServers() throws IOException {
        authMockServer.shutdown();
        eventsMockServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "application.security.jwt.secret-key",
                () -> JWT_SECRET
        );

        registry.add(
                "app.services.auth.base-url",
                () -> authMockServer.url("/").toString()
        );

        registry.add(
                "app.services.auth.client-id",
                () -> "eventpass-ms-orders"
        );

        registry.add(
                "app.services.auth.client-secret",
                () -> "test-ms-orders-secret"
        );

        registry.add(
                "app.services.events.base-url",
                () -> eventsMockServer.url("/").toString()
        );
    }
    private void mockCapacityRelease(
            UUID eventId,
            UUID ticketCategoryId
    ) {
        eventsMockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .setBody(
                                """
                                {
                                  "status": 200,
                                  "message": "Aforo liberado.",
                                  "data": {
                                    "eventId": "%s",
                                    "releasedQuantity": 2,
                                    "success": true
                                  }
                                }
                                """
                                        .formatted(eventId)
                        )
        );
    }
    @Test
    void createAndGetOrder_ReturnsCreatedOrderForOwner()
            throws InterruptedException {

        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketCategoryId = UUID.randomUUID();

        mockAuthServiceToken();
        mockTicketCategory(eventId, ticketCategoryId);
        mockCapacityReservation(eventId, ticketCategoryId);


        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        String requestBody = """
                {
                  "eventId": "%s",
                  "ticketCategoryId": "%s",
                  "quantity": 2
                }
                """.formatted(
                eventId,
                ticketCategoryId
        );

        StandardResponse<OrderResponse> createResponse =
                client.post()
                        .uri("/api/v1/orders")
                        .header(
                                "Authorization",
                                "Bearer " + generateUserJwt(userId)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<
                                        StandardResponse<OrderResponse>
                                        >() {
                                }
                        );

        assertNotNull(createResponse);
        assertNotNull(createResponse.data());

        OrderResponse createdOrder = createResponse.data();

        assertEquals("PENDING", createdOrder.status().name());
        assertEquals(userId, createdOrder.userId());
        assertEquals(
                new BigDecimal("100000.00"),
                createdOrder.totalAmount()
        );
        assertNotNull(createdOrder.id());
        assertEquals(1, createdOrder.items().size());

        StandardResponse<OrderResponse> getResponse =
                client.get()
                        .uri("/api/v1/orders/{id}", createdOrder.id())
                        .header(
                                "Authorization",
                                "Bearer " + generateUserJwt(userId)
                        )
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<
                                        StandardResponse<OrderResponse>
                                        >() {
                                }
                        );

        assertNotNull(getResponse);
        assertNotNull(getResponse.data());

        OrderResponse foundOrder = getResponse.data();

        assertEquals(createdOrder.id(), foundOrder.id());
        assertEquals(userId, foundOrder.userId());
        assertEquals("PENDING", foundOrder.status().name());
        assertEquals(1, foundOrder.items().size());


    }

    private void mockAuthServiceToken() {
        authMockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .setBody(
                                """
                                {
                                  "status": 200,
                                  "message": "Service token generado.",
                                  "data": {
                                    "accessToken": "service-token-test",
                                    "tokenType": "Bearer",
                                    "expiresIn": 3600
                                  }
                                }
                                """
                        )
        );
    }

    private void mockTicketCategory(
            UUID eventId,
            UUID ticketCategoryId
    ) {
        eventsMockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .setBody(
                                """
                                {
                                  "status": 200,
                                  "message": "Categoría obtenida.",
                                  "data": {
                                    "id": "%s",
                                    "name": "General",
                                    "price": 50000.00,
                                    "totalCapacity": 100,
                                    "availableCapacity": 100,
                                    "maxPerUser": 10
                                  }
                                }
                                """
                                        .formatted(ticketCategoryId)
                        )
        );
    }

    private void mockCapacityReservation(
            UUID eventId,
            UUID ticketCategoryId
    ) {
        eventsMockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .setBody(
                                """
                                {
                                  "status": 200,
                                  "message": "Aforo reservado.",
                                  "data": {
                                    "eventId": "%s",
                                    "reservedQuantity": 2,
                                    "success": true
                                  }
                                }
                                """
                                        .formatted(eventId)
                        )
        );
    }

    private String generateUserJwt(UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(JWT_SECRET)
        );

        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userId.toString())
                .claim(
                        "userId",
                        userId.toString()
                )
                .claim(
                        "authorities",
                        List.of("ROLE_CUSTOMER")
                )
                .issuedAt(java.util.Date.from(now))
                .expiration(
                        java.util.Date.from(
                                now.plusSeconds(3600)
                        )
                )
                .signWith(key)
                .compact();
    }

    @Test
    void getOrderById_WhenOrderBelongsToAnotherUser_ReturnsNotFound()
            throws InterruptedException {

        UUID ownerId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketCategoryId = UUID.randomUUID();

        mockAuthServiceToken();
        mockTicketCategory(eventId, ticketCategoryId);
        mockCapacityReservation(eventId, ticketCategoryId);

        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        String requestBody = """
            {
              "eventId": "%s",
              "ticketCategoryId": "%s",
              "quantity": 1
            }
            """.formatted(
                eventId,
                ticketCategoryId
        );

        StandardResponse<OrderResponse> createResponse =
                client.post()
                        .uri("/api/v1/orders")
                        .header(
                                "Authorization",
                                "Bearer " + generateUserJwt(ownerId)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<
                                        StandardResponse<OrderResponse>
                                        >() {
                                }
                        );

        assertNotNull(createResponse);
        assertNotNull(createResponse.data());

        UUID orderId = createResponse.data().id();

        ResponseEntity<StandardResponse<OrderResponse>> response =
                client.get()
                        .uri("/api/v1/orders/{id}", orderId)
                        .header(
                                "Authorization",
                                "Bearer " + generateUserJwt(anotherUserId)
                        )
                        .retrieve()
                        .onStatus(
                                HttpStatusCode::isError,
                                (request, clientResponse) -> {
                                    // Evita que RestClient lance excepción.
                                    // Validamos el status manualmente.
                                }
                        )
                        .toEntity(
                                new ParameterizedTypeReference<
                                        StandardResponse<OrderResponse>
                                        >() {
                                }
                        );

        assertEquals(404, response.getStatusCode().value());
    }
    @Test
    void getMyOrders_ReturnsOnlyOrdersOfAuthenticatedUser()
            throws InterruptedException {

        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketCategoryId = UUID.randomUUID();

        mockAuthServiceToken();

        mockTicketCategory(eventId, ticketCategoryId);
        mockCapacityReservation(eventId, ticketCategoryId);

        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        String firstOrderRequest = """
            {
              "eventId": "%s",
              "ticketCategoryId": "%s",
              "quantity": 1
            }
            """.formatted(
                eventId,
                ticketCategoryId
        );

        client.post()
                .uri("/api/v1/orders")
                .header(
                        "Authorization",
                        "Bearer " + generateUserJwt(userId)
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(firstOrderRequest)
                .retrieve()
                .body(
                        new ParameterizedTypeReference<
                                StandardResponse<OrderResponse>
                                >() {
                        }
                );

        mockTicketCategory(eventId, ticketCategoryId);
        mockCapacityReservation(eventId, ticketCategoryId);

        String secondOrderRequest = """
            {
              "eventId": "%s",
              "ticketCategoryId": "%s",
              "quantity": 2
            }
            """.formatted(
                eventId,
                ticketCategoryId
        );

        client.post()
                .uri("/api/v1/orders")
                .header(
                        "Authorization",
                        "Bearer " + generateUserJwt(userId)
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(secondOrderRequest)
                .retrieve()
                .body(
                        new ParameterizedTypeReference<
                                StandardResponse<OrderResponse>
                                >() {
                        }
                );

        ParameterizedTypeReference<StandardResponse<PageResponse<OrderResponse>>>
                pageType = new ParameterizedTypeReference<>() {
        };

        StandardResponse<PageResponse<OrderResponse>> response =
                client.get()
                        .uri("/api/v1/orders/my-orders?page=0&size=10")
                        .header(
                                "Authorization",
                                "Bearer " + generateUserJwt(userId)
                        )
                        .retrieve()
                        .body(pageType);

        assertNotNull(response);
        assertNotNull(response.data());
        assertEquals(2, response.data().totalElements());
        assertEquals(2, response.data().content().size());

        assertTrue(
                response.data()
                        .content()
                        .stream()
                        .allMatch(order ->
                                order.userId().equals(userId)
                        )
        );
    }

    @Test
    void cancelOrder_WhenOrderIsPending_ReturnsCancelledOrderAndReleasesCapacity()
            throws InterruptedException {

        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketCategoryId = UUID.randomUUID();

        mockAuthServiceToken();
        mockTicketCategory(eventId, ticketCategoryId);
        mockCapacityReservation(eventId, ticketCategoryId);
        mockCapacityRelease(eventId, ticketCategoryId);

        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        String requestBody = """
            {
              "eventId": "%s",
              "ticketCategoryId": "%s",
              "quantity": 2
            }
            """.formatted(
                eventId,
                ticketCategoryId
        );

        StandardResponse<OrderResponse> createResponse =
                client.post()
                        .uri("/api/v1/orders")
                        .header(
                                "Authorization",
                                "Bearer " + generateUserJwt(userId)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<
                                        StandardResponse<OrderResponse>
                                        >() {
                                }
                        );

        assertNotNull(createResponse);
        assertNotNull(createResponse.data());

        UUID orderId = createResponse.data().id();

        StandardResponse<OrderResponse> cancelResponse =
                client.patch()
                        .uri("/api/v1/orders/{id}/cancel", orderId)
                        .header(
                                "Authorization",
                                "Bearer " + generateUserJwt(userId)
                        )
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<
                                        StandardResponse<OrderResponse>
                                        >() {
                                }
                        );

        assertNotNull(cancelResponse);
        assertNotNull(cancelResponse.data());

        OrderResponse cancelledOrder = cancelResponse.data();

        assertEquals(orderId, cancelledOrder.id());
        assertEquals("CANCELLED", cancelledOrder.status().name());
        assertEquals(userId, cancelledOrder.userId());
    }

    @Test
    void payOrder_WhenPaymentIsApproved_ReturnsPaidOrder()
            throws InterruptedException {

        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketCategoryId = UUID.randomUUID();

        mockAuthServiceToken();
        mockTicketCategory(eventId, ticketCategoryId);
        mockCapacityReservation(eventId, ticketCategoryId);

        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        String createOrderRequest = """
            {
              "eventId": "%s",
              "ticketCategoryId": "%s",
              "quantity": 2
            }
            """.formatted(
                eventId,
                ticketCategoryId
        );

        StandardResponse<OrderResponse> createResponse =
                client.post()
                        .uri("/api/v1/orders")
                        .header(
                                "Authorization",
                                "Bearer " + generateUserJwt(userId)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(createOrderRequest)
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<
                                        StandardResponse<OrderResponse>
                                        >() {
                                }
                        );

        assertNotNull(createResponse);
        assertNotNull(createResponse.data());

        UUID orderId = createResponse.data().id();

        String paymentRequest = """
            {
              "paymentMethod": "CARD"
            }
            """;

        StandardResponse<OrderResponse> paymentResponse =
                client.post()
                        .uri("/api/v1/orders/{id}/payment", orderId)
                        .header(
                                "Authorization",
                                "Bearer " + generateUserJwt(userId)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(paymentRequest)
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<
                                        StandardResponse<OrderResponse>
                                        >() {
                                }
                        );

        assertNotNull(paymentResponse);
        assertNotNull(paymentResponse.data());

        OrderResponse paidOrder = paymentResponse.data();

        assertEquals(orderId, paidOrder.id());
        assertEquals("PAID", paidOrder.status().name());
        assertEquals("APPROVED", paidOrder.paymentStatus().name());
        assertEquals(userId, paidOrder.userId());
    }

    /*RECORD */
    record PageResponse<T>(
            List<T> content,
            long totalElements,
            int totalPages,
            int number,
            int size
    ) {
    }
}