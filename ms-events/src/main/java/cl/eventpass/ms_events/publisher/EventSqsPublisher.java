package cl.eventpass.ms_events.publisher;

import cl.eventpass.ms_events.event.EventCancelledEvent;
import cl.eventpass.ms_events.event.EventPublishedEvent;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventSqsPublisher {

    private final SqsTemplate sqsTemplate;

    @Value("${app.sqs.queues.event-published}")
    private String eventPublishedQueue;

    @Value("${app.sqs.queues.event-cancelled}")
    private String eventCancelledQueue;

    public void publishEventPublished(EventPublishedEvent event) {
        log.info("Publicando evento publicado a SQS [eventId: {}]", event.eventId());
        sqsTemplate.send(to -> to.queue(eventPublishedQueue).payload(event));
    }

    public void publishEventCancelled(EventCancelledEvent event) {
        log.info("Publicando evento cancelado a SQS [eventId: {}]", event.eventId());
        sqsTemplate.send(to -> to.queue(eventCancelledQueue).payload(event));
    }
}
