package cl.eventpass.ms_tickets.event;

import java.util.List;
import java.util.UUID;

public record OrderCompletedEvent(
        UUID orderId,
        UUID userId,
        List<OrderCompletedItem> items
) {
}
