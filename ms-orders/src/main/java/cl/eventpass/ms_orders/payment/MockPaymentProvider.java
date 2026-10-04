package cl.eventpass.ms_orders.payment;

import cl.eventpass.ms_orders.dto.request.PaymentRequest;
import cl.eventpass.ms_orders.entity.OrderEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentProvider implements PaymentProvider {

    private final PaymentResult result;

    public MockPaymentProvider(
            @Value("${app.payment.mock.result:APPROVED}")
            String result
    ) {
        this.result = PaymentResult.valueOf(
                result.toUpperCase()
        );
    }

    @Override
    public PaymentResult processPayment(
            OrderEntity order,
            PaymentRequest request
    ) {
        return result;
    }
}
