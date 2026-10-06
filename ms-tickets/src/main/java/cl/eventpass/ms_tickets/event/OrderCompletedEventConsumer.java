package cl.eventpass.ms_tickets.event;

import cl.eventpass.ms_tickets.repository.TicketRepository;
import cl.eventpass.ms_tickets.service.TicketService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompletedEventConsumer {

    private final TicketService ticketService;
    private final TicketRepository ticketRepository;

    @SqsListener("${app.sqs.queues.order-completed}")
    public void handle(OrderCompletedEvent event) {

        log.info(
                "OrderCompletedEvent recibido. orderId={}, userId={}, items={}",
                event.orderId(),
                event.userId(),
                event.items().size()
        );

        ticketService.generateTickets(event);

        log.info(
                "OrderCompletedEvent procesado correctamente. orderId={}",
                event.orderId()
        );
    }

    @SqsListener("${app.sqs.queues.order-completed}")
    public void consume(OrderCompletedEvent event) {
        log.info("Procesando evento order-completed. OrderId: {}", event.getOrderId());

        // Guard de idempotencia usando tu repository
        if (ticketRepository.existsByOrderIdAndDeletedAtIsNull(event.getOrderId())) {
            log.warn("La orden {} ya fue procesada anteriormente. Omitiendo duplicado.", event.getOrderId());
            return;
        }

        ticketService.generateTicketsFromOrder(event);
    }
}
