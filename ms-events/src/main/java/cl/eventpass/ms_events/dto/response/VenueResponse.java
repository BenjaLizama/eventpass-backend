package cl.eventpass.ms_events.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VenueResponse(
        UUID id,
        String name,
        String address,
        String city,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        String placeId,
        Integer capacity,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt
) {
}
