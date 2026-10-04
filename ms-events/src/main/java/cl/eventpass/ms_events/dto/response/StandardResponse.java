package cl.eventpass.ms_events.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import org.springframework.http.HttpStatus;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StandardResponse<T>(
        Integer status,
        String message,
        T data
) {
    // Metodo para respuestas 201
    public static <T> StandardResponse<T> created(String message, T data) {
        return new StandardResponse<>(HttpStatus.CREATED.value(), message, data);
    }

    // Metodo para respuestas 200
    public static <T> StandardResponse<T> ok(String message, T data) {
        return new StandardResponse<>(HttpStatus.OK.value(), message, data);
    }
}
