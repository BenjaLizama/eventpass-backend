package cl.eventpass.ms_orders.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrderCompletedOutbox {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(OrderCompletedApplicationEvent applicationEvent) {
        OrderCompletedEvent event = applicationEvent.event();
        try {
            jdbc.sql("""
                    INSERT INTO order_completed_outbox (order_id, payload)
                    VALUES (:orderId, :payload)
                    """)
                    .param("orderId", event.orderId())
                    .param("payload", mapper.writeValueAsString(event))
                    .update();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo registrar el evento de emisión.", e);
        }
    }
}
