package cl.eventpass.ms_events.service.impl;

import cl.eventpass.ms_events.dto.request.EventCreateRequest;
import cl.eventpass.ms_events.dto.response.EventResponse;
import cl.eventpass.ms_events.enums.EventCategory;
import cl.eventpass.ms_events.service.EventService;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.Page;
import org.springframework.stereotype.Service;

import java.awt.print.Pageable;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    @Override
    public EventResponse createEvent(EventCreateRequest request, UUID organizerId) {
        return null;
    }

    @Override
    public EventResponse getEventById(UUID id) {
        return null;
    }

    @Override
    public Page getPublishedEvents(EventCategory category, Pageable pageable) {
        return null;
    }

    @Override
    public Page getOrganizerEvents(UUID organizerId, Pageable pageable) {
        return null;
    }

    @Override
    public EventResponse publishEvent(UUID eventId, UUID organizerId) {
        return null;
    }

    @Override
    public EventResponse cancelEvent(UUID eventId, UUID organizerId) {
        return null;
    }
}
