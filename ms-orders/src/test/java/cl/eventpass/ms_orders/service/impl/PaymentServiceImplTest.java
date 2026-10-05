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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderCapacityService orderCapacityService;

    @Mock
    private PaymentProvider paymentProvider;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private UUID orderId;
    private UUID userId;
    private PaymentRequest paymentRequest;
    private OrderEntity pendingOrder;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        paymentRequest = new PaymentRequest("CREDIT_CARD");

        pendingOrder = new OrderEntity();
        pendingOrder.setId(orderId);
        pendingOrder.setUserId(userId);
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.setPaymentStatus(PaymentStatus.PENDING);
        pendingOrder.setExpiresAt(
                Instant.now().plus(10, ChronoUnit.MINUTES)
        );
    }

    @Test
    void processPayment_WhenApproved_UpdatesOrderAndPaymentToApproved() {
        when(orderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.of(pendingOrder));

        when(paymentProvider.processPayment(
                pendingOrder,
                paymentRequest
        )).thenReturn(PaymentResult.APPROVED);

        when(orderRepository.save(any(OrderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse expectedResponse = new OrderResponse(
                orderId,
                userId,
                null,
                OrderStatus.PAID,
                PaymentStatus.APPROVED,
                null,
                List.of()
        );

        when(orderMapper.toResponse(
                any(OrderEntity.class),
                anyList()
        )).thenReturn(expectedResponse);

        OrderResponse result = paymentService.processPayment(
                orderId,
                userId,
                paymentRequest
        );

        assertEquals(OrderStatus.PAID, pendingOrder.getStatus());
        assertEquals(
                PaymentStatus.APPROVED,
                pendingOrder.getPaymentStatus()
        );
        assertEquals(expectedResponse, result);

        verify(orderRepository).findByIdAndUserId(orderId, userId);
        verify(paymentProvider).processPayment(
                pendingOrder,
                paymentRequest
        );
        verify(orderRepository).save(pendingOrder);

        verify(orderCapacityService, never())
                .releaseCapacity(any(OrderEntity.class));
    }

    @Test
    void processPayment_WhenRejected_CancelsOrderAndReleasesCapacity() {
        when(orderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.of(pendingOrder));

        when(paymentProvider.processPayment(
                pendingOrder,
                paymentRequest
        )).thenReturn(PaymentResult.REJECTED);

        when(orderRepository.save(any(OrderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse expectedResponse = new OrderResponse(
                orderId,
                userId,
                null,
                OrderStatus.CANCELLED,
                PaymentStatus.REJECTED,
                null,
                List.of()
        );

        when(orderMapper.toResponse(
                any(OrderEntity.class),
                anyList()
        )).thenReturn(expectedResponse);

        OrderResponse result = paymentService.processPayment(
                orderId,
                userId,
                paymentRequest
        );

        assertEquals(
                OrderStatus.CANCELLED,
                pendingOrder.getStatus()
        );
        assertEquals(
                PaymentStatus.REJECTED,
                pendingOrder.getPaymentStatus()
        );
        assertEquals(expectedResponse, result);

        verify(orderRepository).findByIdAndUserId(orderId, userId);
        verify(paymentProvider).processPayment(
                pendingOrder,
                paymentRequest
        );
        verify(orderCapacityService).releaseCapacity(pendingOrder);
        verify(orderRepository).save(pendingOrder);
    }

    @Test
    void processPayment_WhenOrderDoesNotExistOrDoesNotBelongToUser_ThrowsNotFound() {
        when(orderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> paymentService.processPayment(
                        orderId,
                        userId,
                        paymentRequest
                )
        );

        verify(orderRepository).findByIdAndUserId(orderId, userId);
        verifyNoInteractions(
                paymentProvider,
                orderCapacityService,
                orderMapper,
                orderItemRepository
        );
    }

    @Test
    void processPayment_WhenOrderIsNotPending_ThrowsBusinessRuleException() {
        pendingOrder.setStatus(OrderStatus.PAID);

        when(orderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.of(pendingOrder));

        assertThrows(
                BusinessRuleException.class,
                () -> paymentService.processPayment(
                        orderId,
                        userId,
                        paymentRequest
                )
        );

        verify(paymentProvider, never())
                .processPayment(any(), any());
        verify(orderCapacityService, never())
                .releaseCapacity(any(OrderEntity.class));
    }

    @Test
    void processPayment_WhenOrderPaymentWasAlreadyRejected_ThrowsBusinessRuleException() {
        pendingOrder.setStatus(OrderStatus.CANCELLED);
        pendingOrder.setPaymentStatus(PaymentStatus.REJECTED);

        when(orderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.of(pendingOrder));

        assertThrows(
                BusinessRuleException.class,
                () -> paymentService.processPayment(
                        orderId,
                        userId,
                        paymentRequest
                )
        );

        verify(paymentProvider, never())
                .processPayment(any(), any());
        verify(orderCapacityService, never())
                .releaseCapacity(any(OrderEntity.class));
    }

    @Test
    void processPayment_WhenOrderIsExpired_ThrowsBusinessRuleException() {
        pendingOrder.setExpiresAt(
                Instant.now().minus(5, ChronoUnit.MINUTES)
        );

        when(orderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.of(pendingOrder));

        assertThrows(
                BusinessRuleException.class,
                () -> paymentService.processPayment(
                        orderId,
                        userId,
                        paymentRequest
                )
        );

        verify(paymentProvider, never())
                .processPayment(any(), any());
        verify(orderCapacityService, never())
                .releaseCapacity(any(OrderEntity.class));
    }

    @Test
    void processPayment_WhenProviderThrowsException_DoesNotChangeOrderStatus() {
        when(orderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.of(pendingOrder));

        when(paymentProvider.processPayment(
                pendingOrder,
                paymentRequest
        )).thenThrow(new RuntimeException("Payment provider unavailable"));

        assertThrows(
                RuntimeException.class,
                () -> paymentService.processPayment(
                        orderId,
                        userId,
                        paymentRequest
                )
        );

        assertEquals(OrderStatus.PENDING, pendingOrder.getStatus());
        assertEquals(
                PaymentStatus.PENDING,
                pendingOrder.getPaymentStatus()
        );

        verify(orderRepository, never())
                .save(any(OrderEntity.class));
        verify(orderCapacityService, never())
                .releaseCapacity(any(OrderEntity.class));
    }
}