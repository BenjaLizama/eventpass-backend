package cl.eventpass.ms_orders.client;

import cl.eventpass.ms_orders.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class DependencyCircuitBreakerTest {
    private CircuitBreaker circuit() {
        return CircuitBreaker.of("events-test", CircuitBreakerConfig.custom()
                .slidingWindowSize(2).minimumNumberOfCalls(2).failureRateThreshold(50)
                .permittedNumberOfCallsInHalfOpenState(1).build());
    }

    private RestClient client(MockWebServer server, CircuitBreaker circuit) {
        return RestClient.builder().baseUrl(server.url("/").toString())
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory())
                .requestInterceptor(new DependencyCircuitBreaker(circuit)).build();
    }

    @Test
    void outageOpensCircuitSkipsNetworkAndRecoversWithProbeWithoutRetryingMutation() throws Exception {
        try (var server = new MockWebServer()) {
            server.start();
            var circuit = circuit();
            var client = client(server, circuit);
            server.enqueue(new MockResponse().setResponseCode(503));
            server.enqueue(new MockResponse().setResponseCode(503));
            for (int i = 0; i < 2; i++) assertThrows(DependencyUnavailableException.class,
                    () -> client.post().uri("/reserve").body("one-operation").retrieve().toBodilessEntity());
            assertEquals(CircuitBreaker.State.OPEN, circuit.getState());
            assertThrows(DependencyUnavailableException.class,
                    () -> client.post().uri("/reserve").retrieve().toBodilessEntity());
            assertEquals(2, server.getRequestCount());
            circuit.transitionToHalfOpenState();
            server.enqueue(new MockResponse().setBody("recovered"));
            assertEquals("recovered", client.get().retrieve().body(String.class));
            assertEquals(CircuitBreaker.State.CLOSED, circuit.getState());
        }
    }

    @Test
    void businessAndSecurityRejectionsNeverOpenCircuitAndDependenciesAreIndependent() throws Exception {
        try (var server = new MockWebServer()) {
            server.start();
            var events = circuit();
            var auth = circuit();
            var client = client(server, events);
            for (int status : new int[]{401,403,404,409,409,400}) {
                server.enqueue(new MockResponse().setResponseCode(status));
                assertThrows(HttpClientErrorException.class, () -> client.get().retrieve().toBodilessEntity());
            }
            assertEquals(0, events.getMetrics().getNumberOfFailedCalls());
            assertEquals(CircuitBreaker.State.CLOSED, events.getState());
            events.transitionToOpenState();
            assertEquals(CircuitBreaker.State.CLOSED, auth.getState());
        }
    }

    @Test
    void transportFailureOpensCircuit() {
        var circuit = circuit();
        var interceptor = new DependencyCircuitBreaker(circuit);
        for (int i = 0; i < 2; i++) assertThrows(java.io.IOException.class,
                () -> interceptor.intercept(null, new byte[0], (request, body) -> {
                    throw new java.io.IOException("connection lost");
                }));
        assertEquals(CircuitBreaker.State.OPEN, circuit.getState());
    }

    @Test
    void outageResponseIs503AndDoesNotExposeTransportDetails() {
        var handler = new cl.eventpass.ms_orders.exception.GlobalExceptionHandler(
                new cl.eventpass.ms_orders.mapper.ExceptionResponseMapper());
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/orders");
        var response = handler.handleDependencyUnavailable(
                new org.springframework.web.client.ResourceAccessException("private-host:secret"), request);
        assertEquals(503, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("DEPENDENCY_UNAVAILABLE", response.getBody().code());
        assertNull(response.getBody().developerMessage());
        assertFalse(response.getBody().message().contains("secret"));
    }
}
