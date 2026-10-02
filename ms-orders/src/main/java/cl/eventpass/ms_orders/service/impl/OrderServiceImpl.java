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
import cl.eventpass.ms_orders.repository.OrderItemRepository;
import cl.eventpass.ms_orders.repository.OrderRepository;
import cl.eventpass.ms_orders.service.OrderService;
import lombok.RequiredArgsConstructor;
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
            UUID userId,
            String token
    ) {
        System.out.println(">>> TOKEN ENVIADO A MS-EVENTS: " + token);

        TicketCategoryResponse category =
                eventsClient.getTicketCategory(
                        request.eventId(),
                        request.ticketCategoryId(),
                        token
                );

        if (request.quantity() > category.maxPerUser()) {
            throw new IllegalArgumentException(
                    "La cantidad solicitada supera el máximo permitido "
                            + "por usuario para esta categoría."
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
                    reservationRequest,
                    token
            );

            capacityReserved = true;

            Instant expiresAt =
                    Instant.now()
                            .plus(
                                    ORDER_EXPIRATION_MINUTES,
                                    ChronoUnit.MINUTES
                            );

            OrderEntity order = OrderEntity.builder()
                    .userId(userId)
                    .totalAmount(totalAmount)
                    .status(OrderStatus.PENDING)
                    .paymentStatus(PaymentStatus.PENDING)
                    .expiresAt(expiresAt)
                    .build();

            OrderEntity savedOrder =
                    orderRepository.saveAndFlush(order);

            OrderItemEntity orderItem = OrderItemEntity.builder()
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
                            releaseRequest,
                            token
                    );

                } catch (RuntimeException releaseException) {
                    exception.addSuppressed(releaseException);
                }
            }

            throw exception;
        }
    }
}
