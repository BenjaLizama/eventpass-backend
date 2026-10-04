package cl.eventpass.ms_auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ServiceTokenRequest(
        @NotBlank(message = "El client ID es obligatorio.")
        String clientId,

        @NotBlank(message = "El client secret es obligatorio.")
        String clientSecret
) {
}
