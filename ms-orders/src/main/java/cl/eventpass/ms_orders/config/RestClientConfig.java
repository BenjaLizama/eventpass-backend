package cl.eventpass.ms_orders.config;

import cl.eventpass.ms_orders.client.DependencyCircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public CircuitBreakerRegistry dependencyCircuits() {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(10).minimumNumberOfCalls(5).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(2).build());
    }

    private RestClient dependency(String baseUrl, String name, CircuitBreakerRegistry circuits) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(4));
        var circuit = circuits.circuitBreaker(name);
        circuit.getEventPublisher().onStateTransition(event -> System.getLogger(RestClientConfig.class.getName())
                .log(System.Logger.Level.INFO, "Dependency circuit {0}: {1}", name, event.getStateTransition()));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .requestInterceptor(new DependencyCircuitBreaker(circuit)).build();
    }

    @Bean
    public RestClient eventsRestClient(
            @Value("${app.services.events.base-url}") String baseUrl, CircuitBreakerRegistry circuits
    ) {
        return dependency(baseUrl, "events", circuits);
    }

    @Bean
    public RestClient authRestClient(
            @Value("${app.services.auth.base-url}") String baseUrl, CircuitBreakerRegistry circuits
    ) {
        return dependency(baseUrl, "auth", circuits);
    }
}
