package cl.eventpass.ms_events.dto.response;

import java.util.UUID;

public record CapacityReservationResponse(
        UUID eventId,
        Integer reservedQuantity,
        boolean success
) {
}