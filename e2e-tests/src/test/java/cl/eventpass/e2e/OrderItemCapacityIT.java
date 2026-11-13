package cl.eventpass.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OrderItemCapacityIT {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String auth = baseUrl("AUTH_BASE_URL", "http://localhost:8081");
    private final String events = baseUrl("EVENTS_BASE_URL", "http://localhost:8082");

    @Test
    void shouldDecideLastSlotOnceAndRestoreItOnce() throws Exception {
        String clientId = System.getenv("MS_ORDERS_CLIENT_ID");
        String clientSecret = System.getenv("MS_ORDERS_CLIENT_SECRET");
        assumeTrue(clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank(),
                "Define MS_ORDERS_CLIENT_ID y MS_ORDERS_CLIENT_SECRET para probar los endpoints internos.");
        String serviceToken = data(200, post(auth + "/api/v1/auth/service-token", null, Map.of(
                "clientId", clientId, "clientSecret", clientSecret
        ))).path("accessToken").asText();
        String adminToken = data(200, post(auth + "/api/v1/auth/login", null, Map.of(
                "email", System.getenv("E2E_ADMIN_EMAIL"),
                "password", System.getenv("E2E_ADMIN_PASSWORD")
        ))).path("access_token").asText();
        assertFalse(serviceToken.isBlank());
        assertFalse(adminToken.isBlank());

        String venue = data(201, post(events + "/api/v1/venues", adminToken, Map.of(
                "name", "Recinto reserva " + UUID.randomUUID(),
                "address", "Calle de pruebas 123",
                "city", "Santiago",
                "country", "Chile",
                "capacity", 1
        ))).path("id").asText();
        Instant start = Instant.now().plus(7, ChronoUnit.DAYS);
        JsonNode event = data(201, post(events + "/api/v1/events", adminToken, Map.of(
                "title", "Evento reserva " + UUID.randomUUID(),
                "description", "Último cupo",
                "category", "CONCERT",
                "startDate", start.toString(),
                "endDate", start.plus(2, ChronoUnit.HOURS).toString(),
                "venueId", venue,
                "ticketCategories", List.of(Map.of(
                        "name", "General", "price", 10000, "totalCapacity", 1, "maxPerUser", 1
                ))
        )));
        String eventUrl = events + "/api/v1/events/" + event.path("id").asText();
        String category = event.path("ticketCategories").get(0).path("id").asText();
        String categoryUrl = eventUrl + "/ticket-categories/" + category;
        data(200, request("PATCH", eventUrl + "/publish", adminToken, null));
        assertCapacity(categoryUrl, 1);

        String reservationA = eventUrl + "/reservations/" + UUID.randomUUID();
        String reservationB = eventUrl + "/reservations/" + UUID.randomUUID();
        Map<String, Object> purchase = Map.of("ticketCategoryId", category, "quantity", 1);
        assertEquals(403, send(request("PUT", reservationA, adminToken, purchase)).statusCode());

        var a = http.sendAsync(request("PUT", reservationA, serviceToken, purchase), HttpResponse.BodyHandlers.ofString());
        var b = http.sendAsync(request("PUT", reservationB, serviceToken, purchase), HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(a, b).join();
        String statusA = data(200, a.get()).path("status").asText();
        String statusB = data(200, b.get()).path("status").asText();
        assertTrue(List.of(statusA, statusB).containsAll(List.of("RESERVED", "REJECTED")));
        String accepted = statusA.equals("RESERVED") ? reservationA : reservationB;
        String rejected = statusA.equals("REJECTED") ? reservationA : reservationB;
        assertCapacity(categoryUrl, 0);
        assertStatus("RESERVED", request("PUT", accepted, serviceToken, purchase));
        assertEquals(409, send(request("PUT", accepted, serviceToken,
                Map.of("ticketCategoryId", category, "quantity", 2))).statusCode());

        HttpRequest release = request("PATCH", accepted + "/release", serviceToken, null);
        var releaseA = http.sendAsync(release, HttpResponse.BodyHandlers.ofString());
        var releaseB = http.sendAsync(release, HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(releaseA, releaseB).join();
        assertEquals("RELEASED", data(200, releaseA.get()).path("status").asText());
        assertEquals("RELEASED", data(200, releaseB.get()).path("status").asText());
        assertCapacity(categoryUrl, 1);
        assertStatus("RELEASED", request("PUT", accepted, serviceToken, purchase));
        assertStatus("REJECTED", request("PUT", rejected, serviceToken, purchase));
        assertCapacity(categoryUrl, 1);
    }

    private void assertCapacity(String categoryUrl, int expected) throws Exception {
        var result = data(200, request("GET", categoryUrl, null, null));
        assertEquals(expected, result.path("availableCapacity").asInt(-1));
    }

    private void assertStatus(String expected, HttpRequest request) throws Exception {
        assertEquals(expected, data(200, request).path("status").asText());
    }

    private HttpRequest post(String url, String token, Object body) throws Exception {
        return request("POST", url, token, body);
    }

    private HttpRequest request(String method, String url, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body == null) {
            return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        }
        return builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(int expected, HttpRequest request) throws Exception {
        return data(expected, send(request));
    }

    private JsonNode data(int expected, HttpResponse<String> response) throws Exception {
        assertEquals(expected, response.statusCode(), () -> response.request().method() + " " + response.uri());
        return mapper.readTree(response.body()).path("data");
    }

    private static String baseUrl(String variable, String fallback) {
        String value = System.getenv(variable);
        return value == null || value.isBlank() ? fallback : value.replaceAll("/+$", "");
    }
}
