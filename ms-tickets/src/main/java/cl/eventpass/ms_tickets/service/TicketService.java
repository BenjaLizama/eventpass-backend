package cl.eventpass.ms_tickets.service;

import cl.eventpass.ms_tickets.dto.response.TicketResponse;
import cl.eventpass.ms_tickets.event.OrderCompletedEvent;

import java.util.List;
import java.util.UUID;

public interface TicketService {

    void generateTickets(OrderCompletedEvent event);
    List<TicketResponse> getMyTickets(UUID userId);
    TicketResponse getTicketByCode(String ticketCode, UUID userId);
    TicketResponse useTicket(String ticketCode);
    List<TicketResponse> getTicketsByUserId(UUID userId);
}
