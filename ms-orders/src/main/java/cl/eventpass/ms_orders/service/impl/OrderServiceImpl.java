package cl.eventpass.ms_orders.service.impl;

import cl.eventpass.ms_orders.client.EventsClient;
import cl.eventpass.ms_orders.dto.request.CapacityReleaseRequest;
import cl.eventpass.ms_orders.dto.request.CapacityReservationRequest;
import cl.eventpass.ms_orders.dto.request.OrderCreateRequest;
import cl.eventpass.ms_orders.dto.response.OrderItemResponse;
import cl.eventpass.ms_orders.dto.response.OrderResponse;
import cl.eventpass.ms_orders.dto.response.TicketCategoryResponse;
import cl.eventpass.ms_orders.entity.OrderEntity;
import cl.eventpass.ms_orders.entity.OrderItemEntity;
import cl.eventpass.ms_orders.enums.OrderStatus;
import cl.eventpass.ms_orders.enums.PaymentStatus;
import cl.eventpass.ms_orders.exception.BusinessRuleException;
import cl.eventpass.ms_orders.exception.InvalidRequestException;
import cl.eventpass.ms_orders.exception.ResourceNotFoundException;
import cl.eventpass.ms_orders.repository.OrderItemRepository;
import cl.eventpass.ms_orders.repository.OrderRepository;
import cl.eventpass.ms_orders.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final long ORDER_EXPIRATION_MINUTES = 15;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final EventsClient eventsClient;

    @Override
    @Transactional
    public OrderResponse createOrder(
            OrderCreateRequest request,
            UUID userId
    ) {

        TicketCategoryResponse category =
                eventsClient.getTicketCategory(
                        request.eventId(),
                        request.ticketCategoryId()
                );

        orderItemRepository.acquirePurchaseLock(
                userId,
                request.ticketCategoryId()
        );

        Long currentQuantity =
                orderItemRepository.sumQuantityByUserAndTicketCategory(
                        userId,
                        request.ticketCategoryId(),
                        List.of(
                                OrderStatus.PENDING,
                                OrderStatus.PAID
                        )
                );

        long totalRequested =
                currentQuantity + request.quantity();

        if (totalRequested > category.maxPerUser()) {
            throw new InvalidRequestException(
                    "La cantidad solicitada supera el máximo permitido "
                            + "por usuario para esta categoría. "
                            + "Cantidad actual: " + currentQuantity
                            + ", cantidad solicitada: " + request.quantity()
                            + ", máximo permitido: " + category.maxPerUser()
            );
        }

        BigDecimal totalAmount =
                category.price()
                        .multiply(
                                BigDecimal.valueOf(request.quantity())
                        );

        CapacityReservationRequest reservationRequest =
                new CapacityReservationRequest(
                        request.ticketCategoryId(),
                        request.quantity()
                );

        boolean capacityReserved = false;

        try {

            eventsClient.reserveCapacity(
                    request.eventId(),
                    reservationRequest
            );

            capacityReserved = true;

            Instant expiresAt =
                    Instant.now()
                            .plus(
                                    ORDER_EXPIRATION_MINUTES,
                                    ChronoUnit.MINUTES
                            );

            OrderEntity order =
                    OrderEntity.builder()
                            .userId(userId)
                            .totalAmount(totalAmount)
                            .status(OrderStatus.PENDING)
                            .paymentStatus(PaymentStatus.PENDING)
                            .expiresAt(expiresAt)
                            .build();

            OrderEntity savedOrder =
                    orderRepository.saveAndFlush(order);

            OrderItemEntity orderItem =
                    OrderItemEntity.builder()
                            .orderId(savedOrder.getId())
                            .eventId(request.eventId())
                            .ticketCategoryId(request.ticketCategoryId())
                            .quantity(request.quantity())
                            .unitPrice(category.price())
                            .build();

            OrderItemEntity savedItem =
                    orderItemRepository.saveAndFlush(orderItem);

            OrderItemResponse itemResponse =
                    new OrderItemResponse(
                            savedItem.getId(),
                            savedItem.getEventId(),
                            savedItem.getTicketCategoryId(),
                            savedItem.getQuantity(),
                            savedItem.getUnitPrice(),
                            savedItem.getUnitPrice()
                                    .multiply(
                                            BigDecimal.valueOf(
                                                    savedItem.getQuantity()
                                            )
                                    )
                    );

            return new OrderResponse(
                    savedOrder.getId(),
                    savedOrder.getUserId(),
                    savedOrder.getTotalAmount(),
                    savedOrder.getStatus(),
                    savedOrder.getPaymentStatus(),
                    savedOrder.getExpiresAt(),
                    List.of(itemResponse)
            );

        } catch (RuntimeException exception) {

            if (capacityReserved) {

                try {

                    CapacityReleaseRequest releaseRequest =
                            new CapacityReleaseRequest(
                                    request.ticketCategoryId(),
                                    request.quantity()
                            );

                    eventsClient.releaseCapacity(
                            request.eventId(),
                            releaseRequest
                    );

                } catch (RuntimeException releaseException) {

                    exception.addSuppressed(
                            releaseException
                    );
                }
            }

            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(
            UUID orderId,
            UUID userId
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

        return toOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(
            UUID userId,
            Pageable pageable
    ) {

        return orderRepository
                .findAllByUserId(userId, pageable)
                .map(this::toOrderResponse);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, UUID userId) {

        OrderEntity order =
                orderRepository.findByIdAndUserId(orderId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "No se encontró la orden solicitada."
                        ));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleException(
                    "Solo se pueden cancelar órdenes en estado PENDING."
            );
        }

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

        order.setStatus(OrderStatus.CANCELLED);

        OrderEntity savedOrder =
                orderRepository.save(order);

        return toOrderResponse(savedOrder);
    }

    private OrderResponse toOrderResponse(
            OrderEntity order
    ) {

        List<OrderItemEntity> orderItems =
                orderItemRepository.findAllByOrderId(
                        order.getId()
                );

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
