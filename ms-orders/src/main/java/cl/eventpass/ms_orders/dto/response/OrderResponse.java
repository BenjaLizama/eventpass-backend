package cl.eventpass.ms_orders.dto.response;

import cl.eventpass.ms_orders.enums.OrderStatus;
import cl.eventpass.ms_orders.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        BigDecimal totalAmount,
        OrderStatus status,
        PaymentStatus paymentStatus,
        Instant expiresAt,
        List<OrderItemResponse> items
) {
}
