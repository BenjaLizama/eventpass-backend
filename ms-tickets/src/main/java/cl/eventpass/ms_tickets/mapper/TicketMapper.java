package cl.eventpass.ms_tickets.mapper;

import cl.eventpass.ms_tickets.document.TicketDocument;
import cl.eventpass.ms_tickets.dto.response.TicketResponse;
import org.springframework.stereotype.Component;

@Component
public class TicketMapper {

    public TicketResponse toResponse(TicketDocument ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getOrderId(),
                ticket.getOrderItemId(),
                ticket.getTicketIndex(),
                ticket.getUserId(),
                ticket.getEventId(),
                ticket.getTicketCategoryId(),
                ticket.getTicketCode(),
                ticket.getStatus(),
                ticket.getUsedAt(),
                ticket.getCreatedAt()
        );
    }
}
