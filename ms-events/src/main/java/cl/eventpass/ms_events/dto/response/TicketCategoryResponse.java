package cl.eventpass.ms_events.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketCategoryResponse(
        UUID id,
        String name,
        BigDecimal price,
        Integer totalCapacity,
        Integer availableCapacity,
        Integer maxPerUser
) {
}
