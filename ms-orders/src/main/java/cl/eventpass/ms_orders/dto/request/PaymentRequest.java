package cl.eventpass.ms_orders.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentRequest(

        @NotBlank(message = "El método de pago es obligatorio.")
        String paymentMethod

) {
}
