package cl.eventpass.ms_orders.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID eventId,
        UUID ticketCategoryId,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
}
