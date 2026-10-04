package cl.eventpass.ms_auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @JsonProperty("email")
        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "Formato de email inválido.")
        String email,

        @JsonProperty("password")
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, message = "La contraseña debe contener al menos 8 caracteres.")
        String password
) {
}
