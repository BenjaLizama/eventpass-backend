package cl.eventpass.ms_orders.dto.request;

import jakarta.validation.constraints.NotBlank;

@io.swagger.v3.oas.annotations.media.Schema(example = "{\"paymentMethod\":\"CARD\"}")
public record PaymentRequest(

        @NotBlank(message = "El método de pago es obligatorio.")
        String paymentMethod

) {
}
