package cl.eventpass.ms_orders.service;

import cl.eventpass.ms_orders.dto.request.OrderCreateRequest;
import cl.eventpass.ms_orders.dto.response.OrderResponse;

import java.util.UUID;

public interface OrderService {
    OrderResponse createOrder(OrderCreateRequest request, UUID userId);
    OrderResponse getOrderById( UUID orderId, UUID userId );
}
