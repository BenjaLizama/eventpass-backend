package cl.eventpass.ms_orders.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

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
