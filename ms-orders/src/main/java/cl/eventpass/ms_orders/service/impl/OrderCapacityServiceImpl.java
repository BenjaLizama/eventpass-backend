package cl.eventpass.ms_orders.service.impl;

import cl.eventpass.ms_orders.client.EventsClient;
import cl.eventpass.ms_orders.dto.request.CapacityReleaseRequest;
import cl.eventpass.ms_orders.entity.OrderEntity;
import cl.eventpass.ms_orders.entity.OrderItemEntity;
import cl.eventpass.ms_orders.repository.OrderItemRepository;
import cl.eventpass.ms_orders.service.OrderCapacityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCapacityServiceImpl implements OrderCapacityService {

    private final OrderItemRepository orderItemRepository;
    private final EventsClient eventsClient;

    @Override
    public void releaseCapacity(OrderEntity order) {

        List<OrderItemEntity> orderItems =
                orderItemRepository.findAllByOrderId(order.getId());

        for (OrderItemEntity item : orderItems) {

            CapacityReleaseRequest request =
                    new CapacityReleaseRequest(
                            item.getTicketCategoryId(),
                            item.getQuantity()
                    );

            eventsClient.releaseCapacity(
                    item.getEventId(),
                    request
            );
        }
    }
}
