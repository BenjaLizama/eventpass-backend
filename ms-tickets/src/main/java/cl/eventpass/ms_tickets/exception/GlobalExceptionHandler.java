package cl.eventpass.ms_tickets.exception;

import cl.eventpass.ms_tickets.dto.response.StandardErrorResponse;
import cl.eventpass.ms_tickets.mapper.ExceptionResponseMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ExceptionResponseMapper responseMapper;

    public GlobalExceptionHandler(
            ExceptionResponseMapper responseMapper
    ) {
        this.responseMapper = responseMapper;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<StandardErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                ex.getMessage(),
                ex,
                request,
                null
        );
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<StandardErrorResponse> handleBusinessRule(
            BusinessRuleException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.CONFLICT,
                "BUSINESS_RULE_VIOLATION",
                ex.getMessage(),
                ex,
                request,
                null
        );
    }

    @ExceptionHandler({
            InvalidDataAccessApiUsageException.class,
            PropertyReferenceException.class
    })
    public ResponseEntity<StandardErrorResponse> handleInvalidSortProperty(
            Exception ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_SORT_PROPERTY",
                "El parámetro de ordenamiento o filtrado es inválido o hace referencia a una propiedad inexistente.",
                ex,
                request,
                null
        );
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<StandardErrorResponse> handleResourceConflict(
            ResourceConflictException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.CONFLICT,
                "RESOURCE_CONFLICT",
                ex.getMessage(),
                ex,
                request,
                null
        );
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<StandardErrorResponse> handleInvalidRequest(
            InvalidRequestException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                ex.getMessage(),
                ex,
                request,
                null
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<StandardErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new HashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(
                    error.getField(),
                    error.getDefaultMessage()
            );
        }

        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Uno o más campos no son válidos.",
                ex,
                request,
                errors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<StandardErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        if (ex.getCause() instanceof InvalidFormatException cause
                && cause.getTargetType() != null
                && cause.getTargetType().isEnum()) {

            return build(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ENUM_VALUE",
                    "El valor enviado no pertenece a un tipo de dato permitido.",
                    ex,
                    request,
                    null
            );
        }

        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "El cuerpo de la solicitud no cumple con un formato JSON válido.",
                ex,
                request,
                null
        );
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<StandardErrorResponse> handleAuthorizationDenied(
            AuthorizationDeniedException ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "No tienes permisos suficientes para realizar esta operación.",
                ex,
                request,
                null
        );
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<StandardErrorResponse> handleHttpClientError(
            HttpClientErrorException ex,
            HttpServletRequest request
    ) {
        HttpStatus status =
                HttpStatus.valueOf(ex.getStatusCode().value());

        return build(
                status,
                resolveClientErrorCode(status),
                resolveClientErrorMessage(status),
                ex,
                request,
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "Ocurrió un error inesperado en el servidor.",
                ex,
                request,
                null
        );
    }

    private ResponseEntity<StandardErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            Exception exception,
            HttpServletRequest request,
            Map<String, String> validationErrors
    ) {
        StandardErrorResponse response =
                responseMapper.toResponse(
                        status,
                        code,
                        message,
                        exception,
                        request,
                        validationErrors
                );

        return ResponseEntity
                .status(status)
                .body(response);
    }

    private String resolveClientErrorCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "INVALID_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "ACCESS_DENIED";
            case NOT_FOUND -> "RESOURCE_NOT_FOUND";
            case CONFLICT -> "RESOURCE_CONFLICT";
            default -> "EXTERNAL_SERVICE_ERROR";
        };
    }

    private String resolveClientErrorMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST ->
                    "La solicitud enviada al servicio externo no es válida.";

            case UNAUTHORIZED ->
                    "No fue posible autenticar la solicitud ante el servicio externo.";

            case FORBIDDEN ->
                    "No tienes permisos suficientes para realizar esta operación.";

            case NOT_FOUND ->
                    "No se encontró el recurso solicitado.";

            case CONFLICT ->
                    "La operación no pudo completarse debido a un conflicto.";

            default ->
                    "El servicio externo rechazó la solicitud.";
        };
    }
}
