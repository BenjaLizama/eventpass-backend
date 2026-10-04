package cl.eventpass.ms_orders.payment;

import cl.eventpass.ms_orders.dto.request.PaymentRequest;
import cl.eventpass.ms_orders.entity.OrderEntity;

public interface PaymentProvider {
    PaymentResult processPayment(OrderEntity order, PaymentRequest request);
}
