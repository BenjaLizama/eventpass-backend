package cl.eventpass.ms_orders.event;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderCompletedEventListener {

    private final SqsTemplate sqsTemplate;

    @Value("${sqs.queues.order-completed}")
    private String orderCompletedQueue;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCompletedApplicationEvent applicationEvent) {

        sqsTemplate.send(
                to -> to
                        .queue(orderCompletedQueue)
                        .payload(applicationEvent.event())
        );
    }
}
