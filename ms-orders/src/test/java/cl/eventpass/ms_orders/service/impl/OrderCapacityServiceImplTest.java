package cl.eventpass.ms_orders.service.impl;

import cl.eventpass.ms_orders.client.EventsClient;
import cl.eventpass.ms_orders.dto.request.CapacityReleaseRequest;
import cl.eventpass.ms_orders.entity.OrderEntity;
import cl.eventpass.ms_orders.entity.OrderItemEntity;
import cl.eventpass.ms_orders.repository.OrderItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCapacityServiceImplTest {

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private EventsClient eventsClient;

    @InjectMocks
    private OrderCapacityServiceImpl orderCapacityService;

    private UUID orderId;
    private UUID firstEventId;
    private UUID firstCategoryId;
    private UUID secondEventId;
    private UUID secondCategoryId;
    private OrderEntity order;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();

        firstEventId = UUID.randomUUID();
        firstCategoryId = UUID.randomUUID();

        secondEventId = UUID.randomUUID();
        secondCategoryId = UUID.randomUUID();

        order = new OrderEntity();
        order.setId(orderId);
    }

    @Test
    void releaseCapacity_WhenOrderHasItems_ReleasesEachItemWithCorrectData() {
        OrderItemEntity firstItem = createOrderItem(
                firstEventId,
                firstCategoryId,
                2
        );

        OrderItemEntity secondItem = createOrderItem(
                secondEventId,
                secondCategoryId,
                1
        );

        when(orderItemRepository.findAllByOrderId(orderId))
                .thenReturn(List.of(firstItem, secondItem));

        orderCapacityService.releaseCapacity(order);

        ArgumentCaptor<CapacityReleaseRequest> firstRequestCaptor =
                ArgumentCaptor.forClass(CapacityReleaseRequest.class);

        verify(eventsClient).releaseCapacity(
                eq(firstEventId),
                firstRequestCaptor.capture()
        );

        CapacityReleaseRequest firstRequest =
                firstRequestCaptor.getValue();

        assertEquals(
                firstCategoryId,
                firstRequest.ticketCategoryId()
        );
        assertEquals(
                2,
                firstRequest.quantity()
        );

        ArgumentCaptor<CapacityReleaseRequest> secondRequestCaptor =
                ArgumentCaptor.forClass(CapacityReleaseRequest.class);

        verify(eventsClient).releaseCapacity(
                eq(secondEventId),
                secondRequestCaptor.capture()
        );

        CapacityReleaseRequest secondRequest =
                secondRequestCaptor.getValue();

        assertEquals(
                secondCategoryId,
                secondRequest.ticketCategoryId()
        );
        assertEquals(
                1,
                secondRequest.quantity()
        );

        verify(orderItemRepository).findAllByOrderId(orderId);
    }

    @Test
    void releaseCapacity_WhenOrderHasNoItems_DoesNotCallEventsClient() {
        when(orderItemRepository.findAllByOrderId(orderId))
                .thenReturn(List.of());

        orderCapacityService.releaseCapacity(order);

        verify(orderItemRepository).findAllByOrderId(orderId);
        verifyNoInteractions(eventsClient);
    }

    private OrderItemEntity createOrderItem(
            UUID eventId,
            UUID ticketCategoryId,
            int quantity
    ) {
        OrderItemEntity item = new OrderItemEntity();

        item.setEventId(eventId);
        item.setTicketCategoryId(ticketCategoryId);
        item.setQuantity(quantity);

        return item;
    }
}