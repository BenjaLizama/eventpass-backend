package cl.eventpass.ms_tickets.repository;

import cl.eventpass.ms_tickets.document.TicketDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketStore {
    void insertIfAbsent(TicketDocument ticket);
    List<TicketDocument> findByUserIdAndDeletedAtIsNull(UUID userId);
    Optional<TicketDocument> findByTicketCodeAndDeletedAtIsNull(String ticketCode);
    TicketDocument markAsUsed(String ticketCode);
}
