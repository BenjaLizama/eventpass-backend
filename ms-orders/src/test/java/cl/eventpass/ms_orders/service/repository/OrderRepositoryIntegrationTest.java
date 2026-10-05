package cl.eventpass.ms_orders.service.repository;

import cl.eventpass.ms_orders.config.JpaConfig;
import cl.eventpass.ms_orders.entity.OrderEntity;
import cl.eventpass.ms_orders.entity.OrderItemEntity;
import cl.eventpass.ms_orders.enums.OrderStatus;
import cl.eventpass.ms_orders.enums.PaymentStatus;
import cl.eventpass.ms_orders.repository.OrderItemRepository;
import cl.eventpass.ms_orders.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(JpaConfig.class)
class OrderRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("eventpass_orders_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );
        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "create-drop"
        );
    }

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Test
    void saveAndFindByIdAndUserId_ReturnsOnlyOwnerOrder() {
        UUID ownerId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();

        OrderEntity order = orderRepository.saveAndFlush(
                createOrder(
                        ownerId,
                        OrderStatus.PENDING,
                        Instant.now().plus(15, ChronoUnit.MINUTES)
                )
        );

        assertTrue(
                orderRepository.findByIdAndUserId(
                        order.getId(),
                        ownerId
                ).isPresent()
        );

        assertTrue(
                orderRepository.findByIdAndUserId(
                        order.getId(),
                        anotherUserId
                ).isEmpty()
        );
    }

    @Test
    void findAllByUserId_ReturnsOnlyOrdersOfRequestedUser() {
        UUID requestedUserId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();

        orderRepository.saveAndFlush(
                createOrder(
                        requestedUserId,
                        OrderStatus.PENDING,
                        Instant.now().plus(15, ChronoUnit.MINUTES)
                )
        );

        orderRepository.saveAndFlush(
                createOrder(
                        requestedUserId,
                        OrderStatus.PAID,
                        Instant.now().plus(15, ChronoUnit.MINUTES)
                )
        );

        orderRepository.saveAndFlush(
                createOrder(
                        anotherUserId,
                        OrderStatus.PENDING,
                        Instant.now().plus(15, ChronoUnit.MINUTES)
                )
        );

        var page = orderRepository.findAllByUserId(
                requestedUserId,
                PageRequest.of(0, 10)
        );

        assertEquals(2, page.getTotalElements());

        assertTrue(
                page.getContent()
                        .stream()
                        .allMatch(order ->
                                order.getUserId()
                                        .equals(requestedUserId)
                        )
        );
    }

    @Test
    void findExpiredOrders_ReturnsOnlyExpiredPendingOrders() {
        OrderEntity expiredPendingOrder =
                orderRepository.saveAndFlush(
                        createOrder(
                                UUID.randomUUID(),
                                OrderStatus.PENDING,
                                Instant.now().minus(1, ChronoUnit.MINUTES)
                        )
                );

        orderRepository.saveAndFlush(
                createOrder(
                        UUID.randomUUID(),
                        OrderStatus.PENDING,
                        Instant.now().plus(1, ChronoUnit.MINUTES)
                )
        );

        orderRepository.saveAndFlush(
                createOrder(
                        UUID.randomUUID(),
                        OrderStatus.PAID,
                        Instant.now().minus(1, ChronoUnit.MINUTES)
                )
        );

        List<OrderEntity> expiredOrders =
                orderRepository.findExpiredOrders(
                        OrderStatus.PENDING,
                        Instant.now()
                );

        assertEquals(1, expiredOrders.size());
        assertEquals(
                expiredPendingOrder.getId(),
                expiredOrders.getFirst().getId()
        );
    }

    @Test
    void sumQuantityByUserAndTicketCategory_CountsOnlyPendingAndPaidOrders() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        saveOrderWithItem(
                userId,
                eventId,
                categoryId,
                2,
                OrderStatus.PENDING
        );

        saveOrderWithItem(
                userId,
                eventId,
                categoryId,
                3,
                OrderStatus.PAID
        );

        saveOrderWithItem(
                userId,
                eventId,
                categoryId,
                4,
                OrderStatus.CANCELLED
        );

        saveOrderWithItem(
                userId,
                eventId,
                categoryId,
                5,
                OrderStatus.EXPIRED
        );

        var total = orderItemRepository
                .sumQuantityByUserAndTicketCategory(
                        userId,
                        categoryId,
                        List.of(
                                OrderStatus.PENDING,
                                OrderStatus.PAID
                        )
                );

        assertEquals(5L, total);
    }

    @Test
    void sumQuantityByUserAndTicketCategory_WhenNoOrdersMatch_ReturnsZero() {
        var total = orderItemRepository
                .sumQuantityByUserAndTicketCategory(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        List.of(
                                OrderStatus.PENDING,
                                OrderStatus.PAID
                        )
                );

        assertEquals(0L, total);
    }

    @Test
    void findAllByOrderId_ReturnsOnlyItemsForRequestedOrder() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        OrderEntity firstOrder = orderRepository.saveAndFlush(
                createOrder(
                        userId,
                        OrderStatus.PENDING,
                        Instant.now().plus(15, ChronoUnit.MINUTES)
                )
        );

        OrderEntity secondOrder = orderRepository.saveAndFlush(
                createOrder(
                        userId,
                        OrderStatus.PENDING,
                        Instant.now().plus(15, ChronoUnit.MINUTES)
                )
        );

        OrderItemEntity firstOrderItem =
                orderItemRepository.saveAndFlush(
                        createOrderItem(
                                firstOrder.getId(),
                                eventId,
                                categoryId,
                                2
                        )
                );

        orderItemRepository.saveAndFlush(
                createOrderItem(
                        secondOrder.getId(),
                        eventId,
                        categoryId,
                        1
                )
        );

        List<OrderItemEntity> foundItems =
                orderItemRepository.findAllByOrderId(
                        firstOrder.getId()
                );

        assertEquals(1, foundItems.size());
        assertEquals(
                firstOrderItem.getId(),
                foundItems.getFirst().getId()
        );
        assertEquals(
                firstOrder.getId(),
                foundItems.getFirst().getOrderId()
        );
    }

    private OrderEntity createOrder(
            UUID userId,
            OrderStatus status,
            Instant expiresAt
    ) {
        OrderEntity order = new OrderEntity();

        order.setUserId(userId);
        order.setTotalAmount(new BigDecimal("50000.00"));
        order.setStatus(status);
        order.setPaymentStatus(
                status == OrderStatus.PAID
                        ? PaymentStatus.APPROVED
                        : PaymentStatus.PENDING
        );
        order.setExpiresAt(expiresAt);

        return order;
    }

    private void saveOrderWithItem(
            UUID userId,
            UUID eventId,
            UUID categoryId,
            int quantity,
            OrderStatus status
    ) {
        OrderEntity order = orderRepository.saveAndFlush(
                createOrder(
                        userId,
                        status,
                        Instant.now().plus(15, ChronoUnit.MINUTES)
                )
        );

        orderItemRepository.saveAndFlush(
                createOrderItem(
                        order.getId(),
                        eventId,
                        categoryId,
                        quantity
                )
        );
    }

    private OrderItemEntity createOrderItem(
            UUID orderId,
            UUID eventId,
            UUID ticketCategoryId,
            int quantity
    ) {
        OrderItemEntity item = new OrderItemEntity();

        item.setOrderId(orderId);
        item.setEventId(eventId);
        item.setTicketCategoryId(ticketCategoryId);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal("50000.00"));

        return item;
    }
}