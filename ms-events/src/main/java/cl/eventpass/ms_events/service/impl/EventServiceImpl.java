package cl.eventpass.ms_events.service.impl;

import cl.eventpass.ms_events.dto.request.EventCreateRequest;
import cl.eventpass.ms_events.dto.request.TicketCategoryRequest;
import cl.eventpass.ms_events.dto.response.EventResponse;
import cl.eventpass.ms_events.entity.EventEntity;
import cl.eventpass.ms_events.entity.VenueEntity;
import cl.eventpass.ms_events.enums.EventCategory;
import cl.eventpass.ms_events.enums.EventStatus;
import cl.eventpass.ms_events.exception.InvalidRequestException;
import cl.eventpass.ms_events.exception.ResourceConflictException;
import cl.eventpass.ms_events.exception.ResourceNotFoundException;
import cl.eventpass.ms_events.mapper.EventMapper;
import cl.eventpass.ms_events.repository.EventRepository;
import cl.eventpass.ms_events.repository.VenueRepository;
import cl.eventpass.ms_events.service.EventService;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final EventMapper eventMapper;

    @Override
    @Transactional
    public EventResponse createEvent(EventCreateRequest request, UUID organizerId) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new InvalidRequestException("La fecha de término debe ser posterior a la fecha de inicio.");
        }

        VenueEntity venue = venueRepository.findActiveById(request.venueId())
                .orElseThrow(() -> new ResourceNotFoundException("Recinto no encontrado con ID: " + request.venueId()));

        int totalTicketsCapacity = request.ticketCategories().stream()
                .mapToInt(TicketCategoryRequest::totalCapacity)
                .sum();

        if (totalTicketsCapacity > venue.getCapacity()) {
            throw new InvalidRequestException(String.format(
                    "El aforo total de entradas (%d) supera la capacidad máxima del recinto (%d).",
                    totalTicketsCapacity, venue.getCapacity()
            ));
        }

        EventEntity eventEntity = eventMapper.toEntity(request, venue, organizerId);
        EventEntity savedEvent = eventRepository.save(eventEntity);

        return eventMapper.toResponse(savedEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventById(UUID id) {
        EventEntity event = eventRepository.findActiveByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado con ID: " + id));

        return eventMapper.toResponse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventResponse> getPublishedEvents(EventCategory category, Pageable pageable) {
        if (category != null) {
            return eventRepository.findByStatusAndCategoryAndDeletedAtIsNull(EventStatus.PUBLISHED, category, pageable)
                    .map(eventMapper::toResponse);
        }
        return eventRepository.findByStatusAndDeletedAtIsNull(EventStatus.PUBLISHED, pageable)
                .map(eventMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventResponse> getOrganizerEvents(UUID organizerId, Pageable pageable) {
        return eventRepository.findByOrganizerIdAndDeletedAtIsNull(organizerId, pageable)
                .map(eventMapper::toResponse);
    }

    @Override
    @Transactional
    public EventResponse publishEvent(UUID eventId, UUID organizerId) {
        EventEntity event = getOrganizerEventWithPermission(eventId, organizerId);

        if (event.getStatus() == EventStatus.PUBLISHED) {
            throw new ResourceConflictException("El evento ya se encuentra publicado.");
        }

        event.setStatus(EventStatus.PUBLISHED);
        return eventMapper.toResponse(eventRepository.save(event));
    }

    @Override
    @Transactional
    public EventResponse cancelEvent(UUID eventId, UUID organizerId) {
        EventEntity event = getOrganizerEventWithPermission(eventId, organizerId);

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ResourceConflictException("El evento ya ha sido cancelado.");
        }

        event.setStatus(EventStatus.CANCELLED);
        return eventMapper.toResponse(eventRepository.save(event));
    }

    private EventEntity getOrganizerEventWithPermission(UUID eventId, UUID organizerId) {
        EventEntity event = eventRepository.findActiveByIdWithDetails(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado con ID: " + eventId));

        if (!event.getOrganizerId().equals(organizerId)) {
            throw new InvalidRequestException("No tienes permisos para modificar este evento.");
        }

        return event;
    }
}
