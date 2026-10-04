package cl.eventpass.ms_orders.service.impl;

import cl.eventpass.ms_orders.dto.request.PaymentRequest;
import cl.eventpass.ms_orders.dto.response.OrderResponse;
import cl.eventpass.ms_orders.entity.OrderEntity;
import cl.eventpass.ms_orders.enums.OrderStatus;
import cl.eventpass.ms_orders.enums.PaymentStatus;
import cl.eventpass.ms_orders.exception.BusinessRuleException;
import cl.eventpass.ms_orders.exception.ResourceNotFoundException;
import cl.eventpass.ms_orders.mapper.OrderMapper;
import cl.eventpass.ms_orders.payment.PaymentProvider;
import cl.eventpass.ms_orders.payment.PaymentResult;
import cl.eventpass.ms_orders.repository.OrderItemRepository;
import cl.eventpass.ms_orders.repository.OrderRepository;
import cl.eventpass.ms_orders.service.OrderCapacityService;
import cl.eventpass.ms_orders.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderCapacityService orderCapacityService;
    private final PaymentProvider paymentProvider;
    private final OrderMapper orderMapper;

    @Override
    @Transactional
    public OrderResponse processPayment(
            UUID orderId,
            UUID userId,
            PaymentRequest request
    ) {

        OrderEntity order =
                orderRepository.findByIdAndUserId(
                                orderId,
                                userId
                        )
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

        return orderMapper.toResponse(
                savedOrder,
                orderItemRepository.findAllByOrderId(savedOrder.getId())
        );
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
}
