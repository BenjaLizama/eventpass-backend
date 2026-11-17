package cl.eventpass.ms_tickets.repository;

import cl.eventpass.ms_tickets.document.TicketDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!dynamodb")
@RequiredArgsConstructor
public class MongoTicketStore implements TicketStore {
    private final TicketRepository repository;

    @Override
    public void insertIfAbsent(TicketDocument ticket) {
        try {
            repository.insert(ticket);
        } catch (DuplicateKeyException e) {
            if (repository.findByOrderItemIdAndTicketIndexAndDeletedAtIsNull(
                    ticket.getOrderItemId(), ticket.getTicketIndex()).isEmpty()) {
                throw e;
            }
        }
    }

    @Override
    public List<TicketDocument> findByUserIdAndDeletedAtIsNull(UUID userId) {
        return repository.findByUserIdAndDeletedAtIsNull(userId);
    }

    @Override
    public Optional<TicketDocument> findByTicketCodeAndDeletedAtIsNull(String code) {
        return repository.findByTicketCodeAndDeletedAtIsNull(code);
    }

    @Override
    public TicketDocument markAsUsed(String code) {
        return repository.markAsUsed(code);
    }
}
