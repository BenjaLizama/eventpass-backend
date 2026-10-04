package cl.eventpass.ms_orders.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CapacityReservationRequest(
        @NotNull
        UUID ticketCategoryId,

        @NotNull
        @Min(1)
        Integer quantity
) {
}
