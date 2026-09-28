package cl.eventpass.ms_auth.exception;

import cl.eventpass.ms_auth.dto.response.StandardErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<StandardErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", ex.getMessage(), ex, request, null);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<StandardErrorResponse> handleInvalidToken(
            InvalidTokenException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", ex.getMessage(), ex, request, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<StandardErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Credenciales de acceso inválidas", ex, request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<StandardErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Error de validación en los campos recibidos", ex, request, errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Ocurrió un error inesperado en el servidor", ex, request, null);
    }

    private ResponseEntity<StandardErrorResponse> buildErrorResponse(
            HttpStatus status,
            String code,
            String message,
            Exception ex,
            HttpServletRequest request,
            Map<String, String> validationErrors
    ) {
        StandardErrorResponse response = StandardErrorResponse.builder()
                .status(status.value())
                .code(code)
                .error(status.getReasonPhrase())
                .message(message)
                .developerMessage(ex.getClass().getName() + ": " + ex.getLocalizedMessage())
                .path(request.getRequestURI())
                .timestamp(System.currentTimeMillis())
                .validationError(validationErrors)
                .build();

        return ResponseEntity.status(status).body(response);
    }
}
