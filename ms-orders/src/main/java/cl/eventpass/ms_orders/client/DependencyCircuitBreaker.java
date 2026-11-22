package cl.eventpass.ms_orders.client;

import cl.eventpass.ms_orders.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** One independent circuit per dependency; no automatic retry of mutations. */
public final class DependencyCircuitBreaker implements ClientHttpRequestInterceptor {
    private final CircuitBreaker circuit;

    public DependencyCircuitBreaker(CircuitBreaker circuit) {
        this.circuit = circuit;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        if (!circuit.tryAcquirePermission()) throw new DependencyUnavailableException();
        long start = System.nanoTime();
        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException | RuntimeException exception) {
            circuit.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, exception);
            throw exception;
        }
        int status;
        try {
            status = response.getStatusCode().value();
        } catch (IOException exception) {
            response.close();
            circuit.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, exception);
            throw exception;
        }
        if (status >= 500 || status == 429) {
            response.close();
            var exception = new DependencyUnavailableException();
            circuit.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, exception);
            throw exception;
        }
        // Invalid credentials, missing resources and full capacity are not outages.
        if (status >= 400) circuit.releasePermission();
        else circuit.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        return response;
    }
}
