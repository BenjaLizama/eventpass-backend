package cl.eventpass.ms_auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@io.swagger.v3.oas.annotations.media.Schema(example = "{\"email\":\"comprador.actualizado@example.com\"}")
public record UpdateMyProfileRequest(
        @NotBlank(message = "El correo electrónico es obligatorio.")
        @Email(message = "El correo electrónico debe tener un formato válido.")
        String email
) {
}
