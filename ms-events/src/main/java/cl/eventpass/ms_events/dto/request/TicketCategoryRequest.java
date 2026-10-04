package cl.eventpass.ms_events.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TicketCategoryRequest(
        @NotBlank(message = "El nombre de la localidad es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String name,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.0", message = "El precio no puede ser negativo")
        BigDecimal price,

        @NotNull(message = "La capacidad total es obligatoria")
        @Min(value = 1, message = "La capacidad debe ser de al menos 1 entrada")
        Integer totalCapacity,

        @NotNull(message = "El límite por usuario es obligatorio")
        @Min(value = 1, message = "El límite por usuario debe ser al menos 1")
        @Max(value = 20, message = "El límite máximo por compra es de 20 entradas")
        Integer maxPerUser
) {
}
