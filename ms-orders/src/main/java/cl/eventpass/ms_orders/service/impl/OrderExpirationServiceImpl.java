package cl.eventpass.ms_orders.service.impl;

import cl.eventpass.ms_orders.entity.OrderEntity;
import cl.eventpass.ms_orders.enums.OrderStatus;
import cl.eventpass.ms_orders.repository.OrderRepository;
import cl.eventpass.ms_orders.service.OrderCapacityService;
import cl.eventpass.ms_orders.service.OrderExpirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExpirationServiceImpl implements OrderExpirationService {

    private final OrderRepository orderRepository;
    private final OrderCapacityService orderCapacityService;

    @Override
    @Transactional
    public void expireOrders() {

        List<OrderEntity> expiredOrders =
                orderRepository.findExpiredOrders(
                        OrderStatus.PENDING,
                        Instant.now()
                );

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info(
                "Se encontraron {} órdenes pendientes de expiración.",
                expiredOrders.size()
        );

        for (OrderEntity order : expiredOrders) {
            expireOrder(order);
        }
    }

    private void expireOrder(OrderEntity order) {

        orderCapacityService.releaseCapacity(order);

        order.setStatus(OrderStatus.EXPIRED);

        orderRepository.save(order);

        log.info(
                "Orden {} expirada correctamente.",
                order.getId()
        );
    }
}
