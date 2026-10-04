package cl.eventpass.ms_orders.controller;

import cl.eventpass.ms_orders.config.annotation.CurrentUserId;
import cl.eventpass.ms_orders.controller.docs.OrderControllerDocs;
import cl.eventpass.ms_orders.dto.request.OrderCreateRequest;
import cl.eventpass.ms_orders.dto.response.OrderResponse;
import cl.eventpass.ms_orders.dto.response.StandardResponse;
import cl.eventpass.ms_orders.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController implements OrderControllerDocs {

    private final OrderService orderService;

    @Override
    @PostMapping
    public ResponseEntity<StandardResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderCreateRequest request,
            @CurrentUserId UUID userId
    ) {

        OrderResponse response =
                orderService.createOrder(
                        request,
                        userId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        StandardResponse.created(
                                "Orden creada con éxito.",
                                response
                        )
                );
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<StandardResponse<OrderResponse>> getOrderById(
            @PathVariable UUID id,
            @CurrentUserId UUID userId
    ) {

        OrderResponse response =
            orderService.getOrderById(
                id,
                userId
            );

        return ResponseEntity.ok(
            StandardResponse.ok(
                "Orden obtenida con éxito.",
                response
            )
        );
    }

    @Override
    @GetMapping("/my-orders")
    public ResponseEntity<StandardResponse<Page<OrderResponse>>> getMyOrders(
            Pageable pageable,
            @CurrentUserId UUID userId
    ) {

        Page<OrderResponse> response =
            orderService.getMyOrders(
                userId,
                pageable
            );

        return ResponseEntity.ok(
            StandardResponse.ok(
                "Órdenes obtenidas con éxito.",
                response
            )
        );
    }

    @Override
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<StandardResponse<OrderResponse>> cancelOrder(
            @PathVariable UUID id,
            @CurrentUserId UUID userId
    ) {

        OrderResponse response =
            orderService.cancelOrder(
                id,
                userId
            );

        return ResponseEntity.ok(
            StandardResponse.ok(
                "Orden cancelada con éxito.",
                response
            )
        );
    }
}
