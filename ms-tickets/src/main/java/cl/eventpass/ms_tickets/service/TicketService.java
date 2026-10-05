package cl.eventpass.ms_tickets.service;

import cl.eventpass.ms_tickets.event.OrderCompletedEvent;

public interface TicketService {

    void generateTickets(OrderCompletedEvent event);
}
