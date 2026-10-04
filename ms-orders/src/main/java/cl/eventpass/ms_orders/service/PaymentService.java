package cl.eventpass.ms_orders.service;

import cl.eventpass.ms_orders.dto.request.PaymentRequest;
import cl.eventpass.ms_orders.dto.response.OrderResponse;

import java.util.UUID;

public interface PaymentService {
    OrderResponse processPayment(UUID orderId, UUID userId, PaymentRequest request);
}
