package cl.eventpass.ms_auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @JsonProperty("refreshToken")
        @NotBlank(message = "El refresh token es obligatorio.")
        String refreshToken
) {}
