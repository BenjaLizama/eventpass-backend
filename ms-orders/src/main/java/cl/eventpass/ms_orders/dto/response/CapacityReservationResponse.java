package cl.eventpass.ms_orders.dto.response;

import java.util.UUID;

public record CapacityReservationResponse(
        UUID eventId,
        Integer reservedQuantity,
        boolean success
) {
}
