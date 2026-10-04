package cl.eventpass.ms_orders.repository;

import cl.eventpass.ms_orders.entity.OrderEntity;
import cl.eventpass.ms_orders.enums.OrderStatus;
import org.aspectj.weaver.ast.Or;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    @Query("SELECT o FROM OrderEntity o WHERE o.status = :status AND o.expiresAt <= :now")
    List<OrderEntity> findExpiredOrders(OrderStatus status, Instant now);

    Optional<OrderEntity> findByIdAndUserId(UUID id, UUID userId);
    Page<OrderEntity> findAllByUserId(UUID userId, Pageable pageable);
}
