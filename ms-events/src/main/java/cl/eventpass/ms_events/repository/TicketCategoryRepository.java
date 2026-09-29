package cl.eventpass.ms_events.repository;

import cl.eventpass.ms_events.entity.TicketCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketCategoryRepository extends JpaRepository<TicketCategoryEntity, UUID> {

    List<Optional<TicketCategoryEntity>> findByEventIdAndDeletedAtIsNull(UUID eventId);

    Optional<TicketCategoryEntity> findByIdAndDeletedAtIsNull(UUID id);
}
