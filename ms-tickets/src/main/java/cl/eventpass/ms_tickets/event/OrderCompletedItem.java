package cl.eventpass.ms_tickets.event;

import java.util.UUID;

public record OrderCompletedItem(
        UUID orderItemId,
        UUID eventId,
        UUID ticketCategoryId,
        Integer quantity
) {
}
