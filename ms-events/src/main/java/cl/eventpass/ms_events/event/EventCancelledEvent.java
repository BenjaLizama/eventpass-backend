package cl.eventpass.ms_events.event;

import java.time.Instant;
import java.util.UUID;

public record EventCancelledEvent(
        UUID eventId,
        String title,
        UUID organizerId,
        Instant cancelledAt
) {
}
