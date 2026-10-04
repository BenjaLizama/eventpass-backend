package cl.eventpass.ms_orders.service.impl;

import cl.eventpass.ms_orders.client.EventsClient;
import cl.eventpass.ms_orders.dto.request.CapacityReleaseRequest;
import cl.eventpass.ms_orders.entity.OrderEntity;
import cl.eventpass.ms_orders.entity.OrderItemEntity;
import cl.eventpass.ms_orders.enums.OrderStatus;
import cl.eventpass.ms_orders.repository.OrderItemRepository;
import cl.eventpass.ms_orders.repository.OrderRepository;
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
    private final OrderItemRepository orderItemRepository;
    private final EventsClient eventsClient;

    @Override
    @Transactional
    public void expireOrders() {
        List<OrderEntity> expiredOrders = orderRepository.findExpiredOrders(
                OrderStatus.PENDING,
                Instant.now()
        );

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("Se encontraron {} órdenes pendientes de expiración.", expiredOrders.size());

        for (OrderEntity order : expiredOrders) {
            expireOrder(order);
        }
    }

    private void expireOrder(OrderEntity order) {
        List<OrderItemEntity> orderItems = orderItemRepository.findAllByOrderId(order.getId());

        for (OrderItemEntity item : orderItems) {
            CapacityReleaseRequest request = new CapacityReleaseRequest(
                    item.getTicketCategoryId(),
                    item.getQuantity()
            );

            eventsClient.releaseCapacity(
                    item.getEventId(),
                    request
            );
        }

        order.setStatus(OrderStatus.EXPIRED);

        orderRepository.save(order);

        log.info("Orden {} expirada correctamente.", order.getId());
    }
}
