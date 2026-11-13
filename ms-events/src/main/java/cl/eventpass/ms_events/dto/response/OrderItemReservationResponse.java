package cl.eventpass.ms_events.dto.response;

import cl.eventpass.ms_events.enums.CapacityReservationStatus;

import java.util.UUID;

public record OrderItemReservationResponse(
        UUID orderItemId,
        UUID eventId,
        UUID ticketCategoryId,
        int quantity,
        CapacityReservationStatus status
) {
}
