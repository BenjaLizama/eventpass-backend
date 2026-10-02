package cl.eventpass.ms_events.event;

import cl.eventpass.ms_events.enums.EventCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventPublishedEvent(
        UUID eventId,
        String title,
        UUID organizerId,
        UUID venueId,
        EventCategory category,
        Instant startDate,
        Instant endDate,
        List<TicketCategoryPayload> ticketCategories,
        Instant publishedAt
) {
    public record TicketCategoryPayload(
            UUID categoryId,
            String name,
            BigDecimal price,
            Integer totalCapacity
    ) {}
}
