package cl.eventpass.ms_events.dto.response;

import cl.eventpass.ms_events.enums.EventCategory;
import cl.eventpass.ms_events.enums.EventStatus;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventResponse(
        UUID id,
        UUID organizerId,
        String title,
        String description,
        EventCategory category,
        EventStatus status,
        Instant startDate,
        Instant endDate,
        String bannerUrl,
        VenueResponse venue,
        List<@Valid TicketCategoryResponse> ticketCategories,
        Instant createdAt,
        Instant updatedAt
) {
}
