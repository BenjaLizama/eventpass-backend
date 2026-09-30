package cl.eventpass.ms_events.service;

import cl.eventpass.ms_events.dto.request.EventCreateRequest;
import cl.eventpass.ms_events.dto.response.EventResponse;
import cl.eventpass.ms_events.enums.EventCategory;
import org.hibernate.query.Page;

import java.awt.print.Pageable;
import java.util.UUID;

public interface EventService {
    EventResponse createEvent(EventCreateRequest request, UUID organizerId);
    EventResponse getEventById(UUID id);
    Page getPublishedEvents(EventCategory category, Pageable pageable);
    Page getOrganizerEvents(UUID organizerId, Pageable pageable);
    EventResponse publishEvent(UUID eventId, UUID organizerId);
    EventResponse cancelEvent(UUID eventId, UUID organizerId);
}
