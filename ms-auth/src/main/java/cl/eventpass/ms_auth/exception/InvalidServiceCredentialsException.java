package cl.eventpass.ms_auth.exception;

public class InvalidServiceCredentialsException extends RuntimeException {
    public InvalidServiceCredentialsException(String message) {
        super(message);
    }
}
