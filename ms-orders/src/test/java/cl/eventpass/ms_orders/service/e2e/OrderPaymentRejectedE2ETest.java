package cl.eventpass.ms_orders.service.e2e;

import cl.eventpass.ms_orders.dto.response.OrderResponse;
import cl.eventpass.ms_orders.dto.response.StandardResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.payment.mock.result=REJECTED",
                "app.orders.expiration.enabled=false"
        }
)
@Testcontainers
class OrderPaymentRejectedE2ETest {

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

    @Test
    void payOrder_WhenPaymentIsRejected_ReturnsCancelledOrderAndReleasesCapacity() {

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

        OrderResponse rejectedOrder = paymentResponse.data();

        assertEquals(orderId, rejectedOrder.id());
        assertEquals("CANCELLED", rejectedOrder.status().name());
        assertEquals("REJECTED", rejectedOrder.paymentStatus().name());
        assertEquals(userId, rejectedOrder.userId());
    }

    private void mockAuthServiceToken() {
        authMockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
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
                        .setHeader("Content-Type", "application/json")
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
                        .setHeader("Content-Type", "application/json")
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

    private void mockCapacityRelease(
            UUID eventId,
            UUID ticketCategoryId
    ) {
        eventsMockServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
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

    private String generateUserJwt(UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(JWT_SECRET)
        );

        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userId.toString())
                .claim("userId", userId.toString())
                .claim("authorities", List.of("ROLE_CUSTOMER"))
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();
    }
}