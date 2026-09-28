package cl.eventpass.ms_auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @JsonProperty("email")
        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "Formato de email inválido.")
        String email,

        @JsonProperty("password")
        @NotBlank(message = "La contraseña es obligatoria.")
        String password
) {
}
