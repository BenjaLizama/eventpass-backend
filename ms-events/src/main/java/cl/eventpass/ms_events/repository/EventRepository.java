package cl.eventpass.ms_events.repository;

import cl.eventpass.ms_events.entity.EventEntity;
import cl.eventpass.ms_events.enums.EventCategory;
import cl.eventpass.ms_events.enums.EventStatus;
import org.hibernate.query.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.awt.print.Pageable;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    // Carga ansiosa de Venue y TicketCategories en una sola consulta SQL.
    @EntityGraph(attributePaths = {"venue", "ticketCategories"})
    @Query("SELECT e FROM EventEntity e WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<EventEntity> findActiveByIdWhitDetails(@Param("id") UUID id);

    // Busqueda para el catalogo publico (solo eventos en estado PUBLISHED)
    @EntityGraph(attributePaths = {"venue"})
    Page findByStatusAndDeletedAtIsNull(EventStatus status, Pageable pageable);

    // Filtrado de catalogo por categoria y estado
    @EntityGraph(attributePaths = {"venue"})
    Page findByStatusAndCategoryAndDeletedAtIsNull(
            EventStatus status,
            EventCategory category,
            Pageable pageable
    );

    // Consulta para el panel del organizador (mis eventos creados)
    Page findByOrganizerIdAndDeletedAtIsNull(UUID organizerId, Pageable pageable);

    // Busqueda de eventos futuros en un rango de fechas.
    @EntityGraph(attributePaths = {"venue"})
    Page findByStatusAndStartDateAfterAndDeletedAtIsNull(
            EventStatus status,
            Instant startDate,
            Pageable pageable
    );
}
