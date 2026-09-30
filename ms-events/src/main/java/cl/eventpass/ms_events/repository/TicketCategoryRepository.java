package cl.eventpass.ms_events.repository;

import cl.eventpass.ms_events.entity.TicketCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketCategoryRepository extends JpaRepository<TicketCategoryEntity, UUID> {

    @Modifying
    @Query("""
        UPDATE TicketCategoryEntity tc\s
        SET tc.availableCapacity = tc.availableCapacity - :quantity\s
        WHERE tc.id = :ticketCategoryId\s
        AND tc.event.id = :eventId\s
        AND tc.availableCapacity >= :quantity
        """)
    int decrementAvailableCapacity(
            @Param("eventId") UUID eventId,
            @Param("ticketCategoryId") UUID ticketCategoryId,
            @Param("quantity") int quantity
    );

    List<Optional<TicketCategoryEntity>> findByEventIdAndDeletedAtIsNull(UUID eventId);

    Optional<TicketCategoryEntity> findByIdAndDeletedAtIsNull(UUID id);
}
