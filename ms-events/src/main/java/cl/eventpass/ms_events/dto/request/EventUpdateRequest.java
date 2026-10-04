package cl.eventpass.ms_events.dto.request;

import cl.eventpass.ms_events.enums.EventCategory;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record EventUpdateRequest(
        @Size(max = 200, message = "El título no puede superar los 200 caracteres")
        String title,

        String description,

        EventCategory category,

        Instant startDate,

        Instant endDate,

        String bannerUrl,

        UUID venueId
) {
}
