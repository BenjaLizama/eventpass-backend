package cl.eventpass.ms_tickets.repository;

import cl.eventpass.ms_tickets.document.TicketDocument;
import cl.eventpass.ms_tickets.enums.TicketStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class TicketRepositoryCustomImpl
        implements TicketRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public TicketDocument markAsUsed(String ticketCode) {

        Query query = new Query();

        query.addCriteria(
                Criteria.where("ticket_code")
                        .is(ticketCode)
                        .and("status")
                        .is(TicketStatus.ACTIVE.name())
                        .and("deleted_at")
                        .is(null)
        );

        Update update = new Update()
                .set("status", TicketStatus.USED.name())
                .set("used_at", Instant.now())
                .inc("version", 1);

        return mongoTemplate.findAndModify(
                query,
                update,
                TicketDocument.class
        );
    }
}
