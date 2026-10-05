package cl.eventpass.ms_tickets.repository;

import cl.eventpass.ms_tickets.document.TicketDocument;
import cl.eventpass.ms_tickets.enums.TicketStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends MongoRepository<TicketDocument, UUID> {
    Optional<TicketDocument> findByTicketCodeAndDeletedAtIsNull(String ticketCode);
    List<TicketDocument> findByUserIdAndDeletedAtIsNull(UUID userId);
    List<TicketDocument> findByEventIdAndDeletedAtIsNull(UUID eventId);
    boolean existsByOrderIdAndOrderItemIdAndDeletedAtIsNull(UUID orderId, UUID orderItemId);
    List<TicketDocument> findByUserIdAndStatusAndDeletedAtIsNull(UUID userId, TicketStatus status);
    Optional<TicketDocument> findByOrderItemIdAndTicketIndexAndDeletedAtIsNull(UUID orderItemId, Integer ticketIndex);
}
