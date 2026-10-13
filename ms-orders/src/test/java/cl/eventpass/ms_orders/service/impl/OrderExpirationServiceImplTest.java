package cl.eventpass.ms_orders.service.impl;

import cl.eventpass.ms_orders.entity.OrderEntity;
import cl.eventpass.ms_orders.enums.OrderStatus;
import cl.eventpass.ms_orders.repository.OrderRepository;
import cl.eventpass.ms_orders.service.OrderCapacityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderCapacityService orderCapacityService;

    @InjectMocks
    private OrderExpirationServiceImpl orderExpirationService;

    private OrderEntity firstExpiredOrder;
    private OrderEntity secondExpiredOrder;

    @BeforeEach
    void setUp() {
        firstExpiredOrder = createPendingOrder();
        secondExpiredOrder = createPendingOrder();
    }

    @Test
    void expireOrders_WhenOneExpiredOrderExists_ExpiresAndReleasesCapacity() {
        when(orderRepository.findExpiredOrders(
                eq(OrderStatus.PENDING),
                any(Instant.class)
        )).thenReturn(List.of(firstExpiredOrder));

        orderExpirationService.expireOrders();

        assertEquals(
                OrderStatus.EXPIRED,
                firstExpiredOrder.getStatus()
        );

        verify(orderRepository).findExpiredOrders(
                eq(OrderStatus.PENDING),
                any(Instant.class)
        );
        verify(orderCapacityService)
                .releaseCapacity(firstExpiredOrder);
        verify(orderRepository).save(firstExpiredOrder);
    }

    @Test
    void expireOrders_WhenMultipleExpiredOrdersExist_ExpiresAndReleasesEachOne() {
        when(orderRepository.findExpiredOrders(
                eq(OrderStatus.PENDING),
                any(Instant.class)
        )).thenReturn(List.of(
                firstExpiredOrder,
                secondExpiredOrder
        ));

        orderExpirationService.expireOrders();

        assertEquals(
                OrderStatus.EXPIRED,
                firstExpiredOrder.getStatus()
        );
        assertEquals(
                OrderStatus.EXPIRED,
                secondExpiredOrder.getStatus()
        );

        verify(orderCapacityService)
                .releaseCapacity(firstExpiredOrder);
        verify(orderCapacityService)
                .releaseCapacity(secondExpiredOrder);

        verify(orderRepository).save(firstExpiredOrder);
        verify(orderRepository).save(secondExpiredOrder);

        verify(orderCapacityService, times(2))
                .releaseCapacity(any(OrderEntity.class));
        verify(orderRepository, times(2))
                .save(any(OrderEntity.class));
    }

    @Test
    void expireOrders_WhenNoExpiredOrdersExist_DoesNotReleaseOrSave() {
        when(orderRepository.findExpiredOrders(
                eq(OrderStatus.PENDING),
                any(Instant.class)
        )).thenReturn(List.of());

        orderExpirationService.expireOrders();

        verify(orderRepository).findExpiredOrders(
                eq(OrderStatus.PENDING),
                any(Instant.class)
        );
        verify(orderCapacityService, never())
                .releaseCapacity(any(OrderEntity.class));
        verify(orderRepository, never())
                .save(any(OrderEntity.class));
    }

    private OrderEntity createPendingOrder() {
        OrderEntity order = new OrderEntity();

        order.setId(UUID.randomUUID());
        order.setStatus(OrderStatus.PENDING);

        return order;
    }
}