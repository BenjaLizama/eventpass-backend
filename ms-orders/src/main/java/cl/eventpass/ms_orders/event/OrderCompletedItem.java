package cl.eventpass.ms_orders.event;

import java.util.UUID;

public record OrderCompletedItem(
        UUID orderItemId,
        UUID eventId,
        UUID ticketCategoryId,
        Integer quantity
) {
}
