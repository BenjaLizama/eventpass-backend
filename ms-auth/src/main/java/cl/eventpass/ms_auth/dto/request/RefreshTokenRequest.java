package cl.eventpass.ms_auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

@io.swagger.v3.oas.annotations.media.Schema(example = "{\"refreshToken\":\"PEGA_EL_REFRESH_TOKEN_DEL_LOGIN\"}")
public record RefreshTokenRequest(
        @JsonProperty("refreshToken")
        @NotBlank(message = "El refresh token es obligatorio.")
        String refreshToken
) {}
