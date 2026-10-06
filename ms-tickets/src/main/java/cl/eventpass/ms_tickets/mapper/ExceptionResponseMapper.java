package cl.eventpass.ms_tickets.mapper;

import cl.eventpass.ms_tickets.dto.response.StandardErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class ExceptionResponseMapper {

    public StandardErrorResponse toResponse(
            HttpStatus status,
            String code,
            String message,
            Exception exception,
            HttpServletRequest request,
            Map<String, String> validationErrors
    ) {
        return StandardErrorResponse.builder()
                .status(status.value())
                .code(code)
                .error(status.getReasonPhrase())
                .message(message)
                .developerMessage(exception.getMessage())
                .path(request.getRequestURI())
                .timestamp(Instant.now().toEpochMilli())
                .validationError(validationErrors)
                .build();
    }
}
