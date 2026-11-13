package cl.eventpass.ms_events.repository;

import cl.eventpass.ms_events.entity.CapacityReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CapacityReservationRepository extends JpaRepository<CapacityReservation, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO capacity_reservations (order_item_id, event_id, ticket_category_id, quantity, status)
            VALUES (:orderItemId, :eventId, :ticketCategoryId, :quantity, 'REJECTED')
            ON CONFLICT (order_item_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("orderItemId") UUID orderItemId,
            @Param("eventId") UUID eventId,
            @Param("ticketCategoryId") UUID ticketCategoryId,
            @Param("quantity") int quantity
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CapacityReservation r WHERE r.orderItemId = :orderItemId")
    Optional<CapacityReservation> findForUpdate(@Param("orderItemId") UUID orderItemId);
}
