package cl.eventpass.ms_auth.dto.request;

import cl.eventpass.ms_auth.enums.Role;
import jakarta.validation.constraints.Email;

public record UpdateUserRequest(
        @Email(message = "El correo electrónico no es válido.")
        String email,

        Role role
) {
}
