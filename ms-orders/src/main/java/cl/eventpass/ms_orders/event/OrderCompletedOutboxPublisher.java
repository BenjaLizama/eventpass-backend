package cl.eventpass.ms_orders.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.orders.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OrderCompletedOutboxPublisher {

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    private final SqsTemplate sqs;

    @Value("${sqs.queues.order-completed}")
    private String queue;

    @Scheduled(
            initialDelayString = "${app.orders.outbox.poll-delay-ms:1000}",
            fixedDelayString = "${app.orders.outbox.poll-delay-ms:1000}"
    )
    public void publishPending() {
        for (int index = 0; index < 20; index++) {
            if (!Boolean.TRUE.equals(transactions.execute(status -> publishNext()))) {
                return;
            }
        }
    }

    private boolean publishNext() {
        var pending = jdbc.sql("""
                SELECT order_id, payload
                FROM order_completed_outbox
                WHERE next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY next_attempt_at, order_id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """)
                .query((row, index) -> new PendingOrder(
                        row.getObject("order_id", UUID.class), row.getString("payload")))
                .optional();
        if (pending.isEmpty()) {
            return false;
        }

        PendingOrder order = pending.get();
        try {
            OrderCompletedEvent event = mapper.readValue(order.payload(), OrderCompletedEvent.class);
            sqs.send(request -> request.queue(queue).payload(event));
        } catch (RuntimeException | JsonProcessingException e) {
            jdbc.sql("""
                    UPDATE order_completed_outbox
                    SET next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '30 seconds'
                    WHERE order_id = :orderId
                    """).param("orderId", order.id()).update();
            log.warn("Publicación a SQS pendiente de reintento. orderId={}", order.id(), e);
            return true;
        }

        jdbc.sql("DELETE FROM order_completed_outbox WHERE order_id = :orderId")
                .param("orderId", order.id()).update();
        return true;
    }

    private record PendingOrder(UUID id, String payload) {}
}
