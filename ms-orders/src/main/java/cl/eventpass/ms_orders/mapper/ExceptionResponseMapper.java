package cl.eventpass.ms_orders.mapper;

import cl.eventpass.ms_orders.dto.response.StandardErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

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
                .developerMessage(
                        exception.getClass().getName()
                                + ": "
                                + exception.getLocalizedMessage()
                )
                .path(request.getRequestURI())
                .timestamp(System.currentTimeMillis())
                .validationError(validationErrors)
                .build();
    }
}
