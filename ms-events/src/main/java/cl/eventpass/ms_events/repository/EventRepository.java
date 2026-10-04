package cl.eventpass.ms_events.repository;

import cl.eventpass.ms_events.entity.EventEntity;
import cl.eventpass.ms_events.enums.EventCategory;
import cl.eventpass.ms_events.enums.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    @Query("""
        SELECT e
        FROM EventEntity e
        LEFT JOIN FETCH e.ticketCategories
        LEFT JOIN FETCH e.venue
        WHERE e.id = :id
          AND e.deletedAt IS NULL
        """)
    Optional<EventEntity> findActiveByIdWithDetails(@Param("id") UUID id);

    Page<EventEntity> findByStatusAndCategoryAndDeletedAtIsNull(
            EventStatus status,
            EventCategory category,
            Pageable pageable
    );

    Page<EventEntity> findByStatusAndDeletedAtIsNull(
            EventStatus status,
            Pageable pageable
    );

    Page<EventEntity> findByOrganizerIdAndDeletedAtIsNull(
            UUID organizerId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"venue"})
    Page<EventEntity> findByStatusAndStartDateAfterAndDeletedAtIsNull(
            EventStatus status,
            Instant startDate,
            Pageable pageable
    );
}
