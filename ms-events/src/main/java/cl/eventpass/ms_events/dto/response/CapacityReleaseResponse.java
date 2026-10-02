package cl.eventpass.ms_events.dto.response;

import java.util.UUID;

public record CapacityReleaseResponse(
        UUID eventId,
        Integer releasedQuantity,
        boolean success
) {
}
