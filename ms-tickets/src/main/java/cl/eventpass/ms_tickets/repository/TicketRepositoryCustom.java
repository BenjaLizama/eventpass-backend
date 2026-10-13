package cl.eventpass.ms_tickets.repository;

import cl.eventpass.ms_tickets.document.TicketDocument;

public interface TicketRepositoryCustom {

    TicketDocument markAsUsed(String ticketCode);
}
