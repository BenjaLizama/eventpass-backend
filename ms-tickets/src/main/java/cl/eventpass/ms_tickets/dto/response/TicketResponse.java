package cl.eventpass.ms_tickets.dto.response;

import cl.eventpass.ms_tickets.enums.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        UUID orderId,
        UUID orderItemId,
        Integer ticketIndex,
        UUID userId,
        UUID eventId,
        UUID ticketCategoryId,
        String ticketCode,
        TicketStatus status,
        Instant usedAt,
        Instant createdAt
) {
}
