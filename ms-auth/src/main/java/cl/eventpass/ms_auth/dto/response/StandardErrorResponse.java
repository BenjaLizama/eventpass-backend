package cl.eventpass.ms_auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.Map;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StandardErrorResponse(
        Integer status,
        String code,
        String error,
        String message,
        String developerMessage,
        String path,
        Long timestamp,
        Map<String, String> validationError
) {
}
