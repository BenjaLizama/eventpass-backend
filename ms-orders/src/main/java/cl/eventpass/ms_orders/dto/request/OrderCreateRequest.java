package cl.eventpass.ms_orders.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@io.swagger.v3.oas.annotations.media.Schema(description = "Usa los IDs de un evento publicado y su localidad.", example = "{\"eventId\":\"123e4567-e89b-42d3-a456-426614174000\",\"ticketCategoryId\":\"123e4567-e89b-42d3-a456-426614174001\",\"quantity\":1}")
public record OrderCreateRequest(
        @NotNull(message = "El ID del evento es obligatorio.")
        UUID eventId,

        @NotNull(message = "El ID de la categoría de ticket es obligatorio.")
        UUID ticketCategoryId,

        @NotNull(message = "La cantidad es obligatoria.")
        @Min(value = 1, message = "La cantidad debe ser al menos 1.")
        Integer quantity
) {
}
