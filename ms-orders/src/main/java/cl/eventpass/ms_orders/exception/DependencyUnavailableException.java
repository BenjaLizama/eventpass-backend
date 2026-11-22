package cl.eventpass.ms_orders.exception;

public class DependencyUnavailableException extends RuntimeException {
    public DependencyUnavailableException() {
        super("Una dependencia no está disponible temporalmente. Intenta nuevamente más tarde.");
    }
}
