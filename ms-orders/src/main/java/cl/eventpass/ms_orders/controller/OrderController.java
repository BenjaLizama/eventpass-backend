package cl.eventpass.ms_orders.controller;

import cl.eventpass.ms_orders.config.annotation.CurrentUserId;
import cl.eventpass.ms_orders.controller.docs.OrderControllerDocs;
import cl.eventpass.ms_orders.dto.request.OrderCreateRequest;
import cl.eventpass.ms_orders.dto.response.OrderResponse;
import cl.eventpass.ms_orders.dto.response.StandardResponse;
import cl.eventpass.ms_orders.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
            @CurrentUserId UUID userId,
            HttpServletRequest httpRequest
    ) {
        String token = extractBearerToken(httpRequest);

        OrderResponse response = orderService.createOrder(
                request,
                userId,
                token
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

    private String extractBearerToken(HttpServletRequest request) {
        String authorizationHeader =
                request.getHeader("Authorization");

        if (authorizationHeader == null
                || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalStateException(
                    "No se encontró un token Bearer válido."
            );
        }

        return authorizationHeader.substring(7);
    }
}
