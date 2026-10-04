package cl.eventpass.ms_orders.dto.response;

import java.util.UUID;

public record CapacityReleaseResponse(
        UUID eventId,
        UUID ticketCategoryId,
        Integer quantity
) {
}
