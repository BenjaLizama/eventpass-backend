package cl.eventpass.ms_auth.dto.request;

import cl.eventpass.ms_auth.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "Formato de email inválido.")
        String email,

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, message = "La contraseña debe contener al menos 8 caracteres.")
        String password,

        @NotNull(message = "El rol es obligatorio.")
        Role role
) {
}
