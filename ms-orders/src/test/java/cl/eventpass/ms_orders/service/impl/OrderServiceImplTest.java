package cl.eventpass.ms_orders.service.impl;

import cl.eventpass.ms_orders.client.EventsClient;
import cl.eventpass.ms_orders.dto.request.CapacityReleaseRequest;
import cl.eventpass.ms_orders.dto.request.CapacityReservationRequest;
import cl.eventpass.ms_orders.dto.request.OrderCreateRequest;
import cl.eventpass.ms_orders.dto.response.CapacityReservationResponse;
import cl.eventpass.ms_orders.dto.response.OrderResponse;
import cl.eventpass.ms_orders.dto.response.TicketCategoryResponse;
import cl.eventpass.ms_orders.entity.OrderEntity;
import cl.eventpass.ms_orders.entity.OrderItemEntity;
import cl.eventpass.ms_orders.enums.OrderStatus;
import cl.eventpass.ms_orders.exception.BusinessRuleException;
import cl.eventpass.ms_orders.exception.InvalidRequestException;
import cl.eventpass.ms_orders.exception.ResourceNotFoundException;
import cl.eventpass.ms_orders.mapper.OrderMapper;
import cl.eventpass.ms_orders.repository.OrderItemRepository;
import cl.eventpass.ms_orders.repository.OrderRepository;
import cl.eventpass.ms_orders.service.OrderCapacityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private EventsClient eventsClient;

    @Mock
    private OrderCapacityService orderCapacityService;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UUID userId;
    private UUID eventId;
    private UUID categoryId;
    private UUID orderId;
    private OrderCreateRequest createRequest;
    private TicketCategoryResponse categoryResponse;
    private OrderEntity pendingOrder;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        createRequest = new OrderCreateRequest(
                eventId,
                categoryId,
                2
        );

        categoryResponse = new TicketCategoryResponse(
                categoryId,
                "VIP",
                new BigDecimal("50000"),
                100,
                100,
                5
        );

        pendingOrder = new OrderEntity();
        pendingOrder.setId(orderId);
        pendingOrder.setUserId(userId);
        pendingOrder.setStatus(OrderStatus.PENDING);
    }

    @Test
    void createOrder_WhenValid_PersistsPendingOrderAndReservesCapacity() {
        when(eventsClient.getTicketCategory(eventId, categoryId))
                .thenReturn(categoryResponse);

        doNothing().when(orderItemRepository)
                .acquirePurchaseLock(userId, categoryId);

        when(orderItemRepository.sumQuantityByUserAndTicketCategory(
                eq(userId),
                eq(categoryId),
                any()
        )).thenReturn(1L);

        when(eventsClient.reserveCapacity(
                eq(eventId),
                any(CapacityReservationRequest.class)
        )).thenReturn(
                new CapacityReservationResponse(
                        eventId,
                        createRequest.quantity(),
                        true
                )
        );

        OrderEntity savedOrder = new OrderEntity();
        savedOrder.setId(orderId);
        savedOrder.setUserId(userId);
        savedOrder.setStatus(OrderStatus.PENDING);

        when(orderRepository.saveAndFlush(any(OrderEntity.class)))
                .thenReturn(savedOrder);

        OrderItemEntity savedItem = new OrderItemEntity();
        savedItem.setId(UUID.randomUUID());

        when(orderItemRepository.saveAndFlush(any(OrderItemEntity.class)))
                .thenReturn(savedItem);

        OrderResponse expectedResponse = new OrderResponse(
                orderId,
                userId,
                new BigDecimal("100000"),
                OrderStatus.PENDING,
                null,
                null,
                List.of()
        );

        when(orderMapper.toResponse(
                eq(savedOrder),
                eq(List.of(savedItem))
        )).thenReturn(expectedResponse);

        OrderResponse result = orderService.createOrder(
                createRequest,
                userId
        );

        assertNotNull(result);
        assertEquals(expectedResponse, result);

        ArgumentCaptor<CapacityReservationRequest> reservationCaptor =
                ArgumentCaptor.forClass(
                        CapacityReservationRequest.class
                );

        verify(eventsClient).getTicketCategory(
                eventId,
                categoryId
        );
        verify(orderItemRepository).acquirePurchaseLock(
                userId,
                categoryId
        );
        verify(orderItemRepository)
                .sumQuantityByUserAndTicketCategory(
                        eq(userId),
                        eq(categoryId),
                        any()
                );
        verify(eventsClient).reserveCapacity(
                eq(eventId),
                reservationCaptor.capture()
        );

        assertEquals(
                categoryId,
                reservationCaptor.getValue().ticketCategoryId()
        );
        assertEquals(
                2,
                reservationCaptor.getValue().quantity()
        );

        ArgumentCaptor<OrderEntity> orderCaptor =
                ArgumentCaptor.forClass(OrderEntity.class);

        verify(orderRepository).saveAndFlush(
                orderCaptor.capture()
        );

        OrderEntity persistedOrder = orderCaptor.getValue();

        assertEquals(userId, persistedOrder.getUserId());
        assertEquals(
                new BigDecimal("100000"),
                persistedOrder.getTotalAmount()
        );
        assertEquals(OrderStatus.PENDING, persistedOrder.getStatus());
        assertNotNull(persistedOrder.getExpiresAt());
        assertTrue(
                persistedOrder.getExpiresAt().isAfter(Instant.now())
        );

        ArgumentCaptor<OrderItemEntity> itemCaptor =
                ArgumentCaptor.forClass(OrderItemEntity.class);

        verify(orderItemRepository).saveAndFlush(
                itemCaptor.capture()
        );

        OrderItemEntity persistedItem = itemCaptor.getValue();

        assertEquals(orderId, persistedItem.getOrderId());
        assertEquals(eventId, persistedItem.getEventId());
        assertEquals(
                categoryId,
                persistedItem.getTicketCategoryId()
        );
        assertEquals(2, persistedItem.getQuantity());
        assertEquals(
                new BigDecimal("50000"),
                persistedItem.getUnitPrice()
        );

        verify(orderMapper).toResponse(
                savedOrder,
                List.of(savedItem)
        );
    }

    @Test
    void createOrder_WhenQuantityExceedsMaximumPerUser_ThrowsInvalidRequest() {
        when(eventsClient.getTicketCategory(eventId, categoryId))
                .thenReturn(categoryResponse);

        doNothing().when(orderItemRepository)
                .acquirePurchaseLock(userId, categoryId);

        when(orderItemRepository.sumQuantityByUserAndTicketCategory(
                eq(userId),
                eq(categoryId),
                any()
        )).thenReturn(4L);

        assertThrows(
                InvalidRequestException.class,
                () -> orderService.createOrder(
                        createRequest,
                        userId
                )
        );

        verify(orderItemRepository).acquirePurchaseLock(
                userId,
                categoryId
        );
        verify(eventsClient, never()).reserveCapacity(
                any(),
                any()
        );
        verify(orderRepository, never()).saveAndFlush(
                any(OrderEntity.class)
        );
        verify(orderItemRepository, never()).saveAndFlush(
                any(OrderItemEntity.class)
        );
    }

    @Test
    void createOrder_WhenTicketCategoryLookupFails_DoesNotReserveOrPersist() {
        when(eventsClient.getTicketCategory(eventId, categoryId))
                .thenThrow(
                        new IllegalStateException(
                                "Categoría no disponible"
                        )
                );

        assertThrows(
                IllegalStateException.class,
                () -> orderService.createOrder(
                        createRequest,
                        userId
                )
        );

        verify(eventsClient).getTicketCategory(
                eventId,
                categoryId
        );
        verifyNoInteractions(
                orderRepository,
                orderItemRepository,
                orderCapacityService
        );
        verify(eventsClient, never()).reserveCapacity(
                any(),
                any()
        );
    }

    @Test
    void createOrder_WhenReservationFails_DoesNotPersistOrReleaseCapacity() {
        when(eventsClient.getTicketCategory(eventId, categoryId))
                .thenReturn(categoryResponse);

        doNothing().when(orderItemRepository)
                .acquirePurchaseLock(userId, categoryId);

        when(orderItemRepository.sumQuantityByUserAndTicketCategory(
                eq(userId),
                eq(categoryId),
                any()
        )).thenReturn(0L);

        when(eventsClient.reserveCapacity(
                eq(eventId),
                any(CapacityReservationRequest.class)
        )).thenThrow(
                new IllegalStateException(
                        "No hay capacidad disponible"
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> orderService.createOrder(
                        createRequest,
                        userId
                )
        );

        verify(eventsClient).reserveCapacity(
                eq(eventId),
                any(CapacityReservationRequest.class)
        );
        verify(eventsClient, never()).releaseCapacity(
                any(),
                any()
        );
        verify(orderRepository, never()).saveAndFlush(
                any(OrderEntity.class)
        );
        verify(orderItemRepository, never()).saveAndFlush(
                any(OrderItemEntity.class)
        );
    }

    @Test
    void createOrder_WhenOrderPersistenceFails_ReleasesReservedCapacity() {
        when(eventsClient.getTicketCategory(eventId, categoryId))
                .thenReturn(categoryResponse);

        doNothing().when(orderItemRepository)
                .acquirePurchaseLock(userId, categoryId);

        when(orderItemRepository.sumQuantityByUserAndTicketCategory(
                eq(userId),
                eq(categoryId),
                any()
        )).thenReturn(0L);

        when(eventsClient.reserveCapacity(
                eq(eventId),
                any(CapacityReservationRequest.class)
        )).thenReturn(
                new CapacityReservationResponse(
                        eventId,
                        createRequest.quantity(),
                        true
                )
        );

        when(orderRepository.saveAndFlush(any(OrderEntity.class)))
                .thenThrow(
                        new RuntimeException("Error de persistencia")
                );

        assertThrows(
                RuntimeException.class,
                () -> orderService.createOrder(
                        createRequest,
                        userId
                )
        );

        ArgumentCaptor<CapacityReleaseRequest> releaseCaptor =
                ArgumentCaptor.forClass(CapacityReleaseRequest.class);

        verify(eventsClient).releaseCapacity(
                eq(eventId),
                releaseCaptor.capture()
        );

        assertEquals(
                categoryId,
                releaseCaptor.getValue().ticketCategoryId()
        );
        assertEquals(
                2,
                releaseCaptor.getValue().quantity()
        );

        verify(orderItemRepository, never()).saveAndFlush(
                any(OrderItemEntity.class)
        );
    }

    @Test
    void getOrderById_WhenOrderExistsAndBelongsToUser_ReturnsMappedOrder() {
        OrderItemEntity item = new OrderItemEntity();
        item.setId(UUID.randomUUID());

        when(orderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.of(pendingOrder));

        when(orderItemRepository.findAllByOrderId(orderId))
                .thenReturn(List.of(item));

        OrderResponse expectedResponse = new OrderResponse(
                orderId,
                userId,
                null,
                OrderStatus.PENDING,
                null,
                null,
                List.of()
        );

        when(orderMapper.toResponse(
                pendingOrder,
                List.of(item)
        )).thenReturn(expectedResponse);

        OrderResponse result = orderService.getOrderById(
                orderId,
                userId
        );

        assertEquals(expectedResponse, result);

        verify(orderRepository).findByIdAndUserId(
                orderId,
                userId
        );
        verify(orderItemRepository).findAllByOrderId(orderId);
        verify(orderMapper).toResponse(
                pendingOrder,
                List.of(item)
        );
    }

    @Test
    void getOrderById_WhenOrderDoesNotExistOrBelongToUser_ThrowsNotFound() {
        when(orderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.getOrderById(orderId, userId)
        );

        verify(orderRepository).findByIdAndUserId(
                orderId,
                userId
        );
        verifyNoInteractions(
                orderItemRepository,
                orderMapper,
                eventsClient,
                orderCapacityService
        );
    }

    @Test
    void getMyOrders_WhenOrdersExist_ReturnsOnlyMappedUserOrders() {
        PageRequest pageRequest = PageRequest.of(0, 10);

        var ordersPage = new PageImpl<>(
                List.of(pendingOrder)
        );

        when(orderRepository.findAllByUserId(
                userId,
                pageRequest
        )).thenReturn(ordersPage);

        OrderItemEntity item = new OrderItemEntity();
        item.setId(UUID.randomUUID());

        when(orderItemRepository.findAllByOrderId(orderId))
                .thenReturn(List.of(item));

        OrderResponse expectedResponse = new OrderResponse(
                orderId,
                userId,
                null,
                OrderStatus.PENDING,
                null,
                null,
                List.of()
        );

        when(orderMapper.toResponse(
                pendingOrder,
                List.of(item)
        )).thenReturn(expectedResponse);

        var result = orderService.getMyOrders(
                userId,
                pageRequest
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(orderId, result.getContent().getFirst().id());

        verify(orderRepository).findAllByUserId(
                userId,
                pageRequest
        );
        verify(orderItemRepository).findAllByOrderId(orderId);
        verify(orderMapper).toResponse(
                pendingOrder,
                List.of(item)
        );
    }

    @Test
    void getMyOrders_WhenUserHasNoOrders_ReturnsEmptyPage() {
        PageRequest pageRequest = PageRequest.of(0, 10);

        when(orderRepository.findAllByUserId(
                userId,
                pageRequest
        )).thenReturn(Page.empty(pageRequest));

        var result = orderService.getMyOrders(
                userId,
                pageRequest
        );

        assertTrue(result.isEmpty());

        verify(orderRepository).findAllByUserId(
                userId,
                pageRequest
        );
        verifyNoInteractions(
                orderItemRepository,
                orderMapper
        );
    }

    @Test
    void cancelOrder_WhenPending_CancelsAndReleasesCapacity() {
        when(orderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.of(pendingOrder));

        when(orderRepository.save(pendingOrder))
                .thenReturn(pendingOrder);

        OrderItemEntity item = new OrderItemEntity();
        item.setId(UUID.randomUUID());

        when(orderItemRepository.findAllByOrderId(orderId))
                .thenReturn(List.of(item));

        OrderResponse expectedResponse = new OrderResponse(
                orderId,
                userId,
                null,
                OrderStatus.CANCELLED,
                null,
                null,
                List.of()
        );

        when(orderMapper.toResponse(
                pendingOrder,
                List.of(item)
        )).thenReturn(expectedResponse);

        OrderResponse result = orderService.cancelOrder(
                orderId,
                userId
        );

        assertEquals(OrderStatus.CANCELLED, result.status());

        verify(orderCapacityService).releaseCapacity(pendingOrder);
        verify(orderRepository).save(pendingOrder);
        verify(orderItemRepository).findAllByOrderId(orderId);
        verify(orderMapper).toResponse(
                pendingOrder,
                List.of(item)
        );
    }

    @Test
    void cancelOrder_WhenOrderDoesNotExistOrBelongToUser_ThrowsNotFound() {
        when(orderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.cancelOrder(orderId, userId)
        );

        verify(orderRepository).findByIdAndUserId(
                orderId,
                userId
        );
        verifyNoInteractions(
                orderCapacityService,
                orderItemRepository,
                orderMapper
        );
        verify(orderRepository, never()).save(
                any(OrderEntity.class)
        );
    }

    @Test
    void cancelOrder_WhenOrderIsNotPending_ThrowsBusinessRuleAndDoesNotReleaseCapacity() {
        pendingOrder.setStatus(OrderStatus.PAID);

        when(orderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.of(pendingOrder));

        assertThrows(
                BusinessRuleException.class,
                () -> orderService.cancelOrder(orderId, userId)
        );

        verify(orderCapacityService, never())
                .releaseCapacity(any(OrderEntity.class));
        verify(orderRepository, never()).save(
                any(OrderEntity.class)
        );
        verifyNoInteractions(
                orderItemRepository,
                orderMapper
        );
    }
}