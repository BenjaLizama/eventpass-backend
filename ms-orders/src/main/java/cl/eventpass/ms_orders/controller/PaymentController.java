package cl.eventpass.ms_orders.controller;

import cl.eventpass.ms_orders.config.annotation.CurrentUserId;
import cl.eventpass.ms_orders.controller.docs.PaymentControllerDocs;
import cl.eventpass.ms_orders.dto.request.PaymentRequest;
import cl.eventpass.ms_orders.dto.response.OrderResponse;
import cl.eventpass.ms_orders.dto.response.StandardResponse;
import cl.eventpass.ms_orders.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class PaymentController implements PaymentControllerDocs {

    private final PaymentService paymentService;

    @Override
    @PostMapping("/{id}/payment")
    public ResponseEntity<StandardResponse<OrderResponse>> processPayment(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentRequest request,
            @CurrentUserId UUID userId
    ) {

        OrderResponse response =
                paymentService.processPayment(
                        id,
                        userId,
                        request
                );

        return ResponseEntity.ok(
                StandardResponse.ok(
                        "Pago procesado correctamente.",
                        response
                )
        );
    }
}
