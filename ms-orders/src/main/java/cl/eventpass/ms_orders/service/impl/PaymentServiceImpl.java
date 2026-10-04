package cl.eventpass.ms_orders.service.impl;

import cl.eventpass.ms_orders.dto.request.PaymentRequest;
import cl.eventpass.ms_orders.dto.response.OrderItemResponse;
import cl.eventpass.ms_orders.dto.response.OrderResponse;
import cl.eventpass.ms_orders.entity.OrderEntity;
import cl.eventpass.ms_orders.entity.OrderItemEntity;
import cl.eventpass.ms_orders.enums.OrderStatus;
import cl.eventpass.ms_orders.enums.PaymentStatus;
import cl.eventpass.ms_orders.exception.BusinessRuleException;
import cl.eventpass.ms_orders.exception.ResourceNotFoundException;
import cl.eventpass.ms_orders.payment.PaymentProvider;
import cl.eventpass.ms_orders.payment.PaymentResult;
import cl.eventpass.ms_orders.repository.OrderItemRepository;
import cl.eventpass.ms_orders.repository.OrderRepository;
import cl.eventpass.ms_orders.service.OrderCapacityService;
import cl.eventpass.ms_orders.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderCapacityService orderCapacityService;
    private final PaymentProvider paymentProvider;

    @Override
    @Transactional
    public OrderResponse processPayment(
            UUID orderId,
            UUID userId,
            PaymentRequest request
    ) {

        OrderEntity order =
                orderRepository.findByIdAndUserId(orderId, userId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "No se encontró la orden solicitada."
                                )
                        );

        validateOrderForPayment(order);

        PaymentResult result =
                paymentProvider.processPayment(
                        order,
                        request
                );

        if (result == PaymentResult.APPROVED) {

            order.setPaymentStatus(PaymentStatus.APPROVED);
            order.setStatus(OrderStatus.PAID);

        } else {

            orderCapacityService.releaseCapacity(order);

            order.setPaymentStatus(PaymentStatus.REJECTED);
            order.setStatus(OrderStatus.CANCELLED);
        }

        OrderEntity savedOrder =
                orderRepository.save(order);

        return toOrderResponse(savedOrder);
    }

    private void validateOrderForPayment(OrderEntity order) {

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleException(
                    "Solo se pueden procesar pagos de órdenes en estado PENDING."
            );
        }

        if (order.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new BusinessRuleException(
                    "La orden ya tiene un resultado de pago."
            );
        }

        if (!order.getExpiresAt().isAfter(Instant.now())) {
            throw new BusinessRuleException(
                    "La orden ha expirado y no puede procesarse el pago."
            );
        }
    }

    private OrderResponse toOrderResponse(OrderEntity order) {

        List<OrderItemEntity> orderItems =
                orderItemRepository.findAllByOrderId(order.getId());

        List<OrderItemResponse> itemResponses =
                orderItems.stream()
                        .map(item ->
                                new OrderItemResponse(
                                        item.getId(),
                                        item.getEventId(),
                                        item.getTicketCategoryId(),
                                        item.getQuantity(),
                                        item.getUnitPrice(),
                                        item.getUnitPrice()
                                                .multiply(
                                                        BigDecimal.valueOf(
                                                                item.getQuantity()
                                                        )
                                                )
                                )
                        )
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
}
