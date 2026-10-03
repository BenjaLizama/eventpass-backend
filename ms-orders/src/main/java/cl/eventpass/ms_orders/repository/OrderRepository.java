package cl.eventpass.ms_orders.repository;

import cl.eventpass.ms_orders.entity.OrderEntity;
import org.aspectj.weaver.ast.Or;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Optional<OrderEntity> findByIdAndUserId(UUID id, UUID userId);
    Page<OrderEntity> findAllByUserId(UUID userId, Pageable pageable);
}
