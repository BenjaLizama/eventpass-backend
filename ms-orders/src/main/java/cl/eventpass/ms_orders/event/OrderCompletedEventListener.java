package cl.eventpass.ms_orders.event;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompletedEventListener {

    private final SqsTemplate sqsTemplate;

    @Value("${sqs.queues.order-completed}")
    private String orderCompletedQueue;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCompletedApplicationEvent applicationEvent) {

        log.info(
                "OrderCompletedApplicationEvent recibido. orderId={}, queue={}",
                applicationEvent.event().orderId(),
                orderCompletedQueue
        );

        try {

            sqsTemplate.send(
                    to -> to
                            .queue(orderCompletedQueue)
                            .payload(applicationEvent.event())
            );

            log.info(
                    "OrderCompletedEvent enviado a SQS. orderId={}, queue={}",
                    applicationEvent.event().orderId(),
                    orderCompletedQueue
            );

        } catch (Exception exception) {

            log.error(
                    "Error enviando OrderCompletedEvent a SQS. orderId={}, queue={}",
                    applicationEvent.event().orderId(),
                    orderCompletedQueue,
                    exception
            );

            throw exception;
        }
    }
}
