package cl.eventpass.ms_orders.mapper;

import cl.eventpass.ms_orders.dto.response.OrderItemResponse;
import cl.eventpass.ms_orders.dto.response.OrderResponse;
import cl.eventpass.ms_orders.entity.OrderEntity;
import cl.eventpass.ms_orders.entity.OrderItemEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(
            OrderEntity order,
            List<OrderItemEntity> items
    ) {

        List<OrderItemResponse> itemResponses =
                items.stream()
                        .map(this::toItemResponse)
                        .toList();

        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getExpiresAt(),
                itemResponses
        );
    }

    private OrderItemResponse toItemResponse(
            OrderItemEntity item
    ) {

        BigDecimal subtotal =
                item.getUnitPrice()
                        .multiply(
                                BigDecimal.valueOf(
                                        item.getQuantity()
                                )
                        );

        return new OrderItemResponse(
                item.getId(),
                item.getEventId(),
                item.getTicketCategoryId(),
                item.getQuantity(),
                item.getUnitPrice(),
                subtotal
        );
    }
}
