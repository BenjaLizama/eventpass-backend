package cl.eventpass.ms_orders.repository;

import cl.eventpass.ms_orders.entity.OrderItemEntity;
import cl.eventpass.ms_orders.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> {

    @Query("""
            SELECT COALESCE(SUM(oi.quantity), 0)
            FROM OrderItemEntity oi
            JOIN OrderEntity o ON o.id = oi.orderId
            WHERE o.userId = :userId
              AND oi.ticketCategoryId = :ticketCategoryId
              AND o.status IN :statuses
            """)
    Long sumQuantityByUserAndTicketCategory(
            UUID userId,
            UUID ticketCategoryId,
            Collection<OrderStatus> statuses
    );

    @Query(value = "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:userId AS text) || ':' ||CAST(:ticketCategoryId AS text),0))", nativeQuery = true)
    void acquirePurchaseLock(
            UUID userId,
            UUID ticketCategoryId
    );
}
