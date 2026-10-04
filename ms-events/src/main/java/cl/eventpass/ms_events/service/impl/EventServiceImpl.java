package cl.eventpass.ms_events.service.impl;

import cl.eventpass.ms_events.dto.request.EventCreateRequest;
import cl.eventpass.ms_events.dto.request.EventUpdateRequest;
import cl.eventpass.ms_events.dto.request.TicketCategoryRequest;
import cl.eventpass.ms_events.dto.response.CapacityReleaseResponse;
import cl.eventpass.ms_events.dto.response.CapacityReservationResponse;
import cl.eventpass.ms_events.dto.response.EventResponse;
import cl.eventpass.ms_events.entity.EventEntity;
import cl.eventpass.ms_events.entity.TicketCategoryEntity;
import cl.eventpass.ms_events.entity.VenueEntity;
import cl.eventpass.ms_events.enums.EventCategory;
import cl.eventpass.ms_events.enums.EventStatus;
import cl.eventpass.ms_events.event.EventCancelledEvent;
import cl.eventpass.ms_events.event.EventPublishedEvent;
import cl.eventpass.ms_events.exception.InvalidRequestException;
import cl.eventpass.ms_events.exception.ResourceConflictException;
import cl.eventpass.ms_events.exception.ResourceNotFoundException;
import cl.eventpass.ms_events.mapper.EventMapper;
import cl.eventpass.ms_events.publisher.EventSqsPublisher;
import cl.eventpass.ms_events.repository.EventRepository;
import cl.eventpass.ms_events.repository.TicketCategoryRepository;
import cl.eventpass.ms_events.repository.VenueRepository;
import cl.eventpass.ms_events.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final EventMapper eventMapper;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final EventSqsPublisher eventSqsPublisher;

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
        Page<EventEntity> events =
                eventRepository.findByOrganizerIdAndDeletedAtIsNull(
                        organizerId,
                        pageable
                );

        return events.map(eventMapper::toResponse);
    }

    @Override
    @Transactional
    public EventResponse updateEvent(UUID eventId, EventUpdateRequest request, UUID userId) {
        EventEntity event = getOrganizerEventWithPermission(eventId, userId);

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new InvalidRequestException("No se puede modificar un evento cancelado.");
        }

        if (request.title() != null) event.setTitle(request.title());
        if (request.description() != null) event.setDescription(request.description());
        if (request.category() != null) event.setCategory(request.category());
        if (request.bannerUrl() != null) event.setBannerUrl(request.bannerUrl());

        if (request.startDate() != null || request.endDate() != null) {
            Instant start = request.startDate() != null ? request.startDate() : event.getStartDate();
            Instant end = request.endDate() != null ? request.endDate() : event.getEndDate();

            if (end.isBefore(start)) {
                throw new InvalidRequestException("La fecha de término debe ser posterior a la fecha de inicio.");
            }
            event.setStartDate(start);
            event.setEndDate(end);
        }

        if (request.venueId() != null && !request.venueId().equals(event.getVenue().getId())) {
            VenueEntity newVenue = venueRepository.findActiveById(request.venueId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recinto no encontrado con ID: " + request.venueId()));

            int totalTicketsCapacity = event.getTicketCategories().stream()
                    .mapToInt(TicketCategoryEntity::getTotalCapacity)
                    .sum();

            if (totalTicketsCapacity > newVenue.getCapacity()) {
                throw new InvalidRequestException(String.format(
                        "El aforo acumulado del evento (%d) supera la capacidad del nuevo recinto (%d).",
                        totalTicketsCapacity, newVenue.getCapacity()
                ));
            }
            event.setVenue(newVenue);
        }

        return eventMapper.toResponse(eventRepository.save(event));
    }

    @Override
    @Transactional
    public CapacityReservationResponse reserveCapacity(UUID eventId, UUID ticketCategoryId, int quantity) {
        EventEntity event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado con ID: " + eventId));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new InvalidRequestException("Solo se puede reservar aforo para eventos publicados.");
        }

        int rowsUpdated = ticketCategoryRepository.decrementAvailableCapacity(eventId, ticketCategoryId, quantity);

        if (rowsUpdated == 0) {
            throw new InvalidRequestException("Sin aforo suficiente en la localidad seleccionada.");
        }

        return new CapacityReservationResponse(eventId, quantity, true);
    }

    @Override
    @Transactional
    public CapacityReleaseResponse releaseCapacity(
            UUID eventId,
            UUID ticketCategoryId,
            Integer quantity
    ) {
        EventEntity event = eventRepository.findById(eventId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No se encontró el evento solicitado."
                        )
                );

        if (event.isDeleted()) {
            throw new ResourceNotFoundException(
                    "No se encontró el evento solicitado."
            );
        }

        TicketCategoryEntity ticketCategory =
                ticketCategoryRepository.findById(ticketCategoryId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "No se encontró la categoría de ticket solicitada."
                                )
                        );

        if (!ticketCategory.getEvent().getId().equals(eventId)) {
            throw new IllegalArgumentException(
                    "La categoría de ticket no pertenece al evento indicado."
            );
        }

        int updatedRows =
                ticketCategoryRepository.incrementAvailableCapacity(
                        ticketCategoryId,
                        quantity
                );

        if (updatedRows == 0) {
            throw new IllegalStateException(
                    "No fue posible liberar el aforo reservado."
            );
        }

        return new CapacityReleaseResponse(
                eventId,
                quantity,
                true
        );
    }

    @Override
    @Transactional
    public EventResponse publishEvent(UUID eventId, UUID userId) {
        EventEntity event = getOrganizerEventWithPermission(eventId, userId);

        if (event.getStatus() == EventStatus.PUBLISHED) {
            throw new ResourceConflictException("El evento ya se encuentra publicado.");
        }

        event.setStatus(EventStatus.PUBLISHED);
        EventEntity savedEvent = eventRepository.save(event);

        var ticketPayloads = savedEvent.getTicketCategories().stream()
                .map(tc -> new EventPublishedEvent.TicketCategoryPayload(
                        tc.getId(),
                        tc.getName(),
                        tc.getPrice(),
                        tc.getTotalCapacity()
                ))
                .toList();

        EventPublishedEvent publishedEvent = new EventPublishedEvent(
                savedEvent.getId(),
                savedEvent.getTitle(),
                savedEvent.getOrganizerId(),
                savedEvent.getVenue().getId(),
                savedEvent.getCategory(), // Enum EventCategory
                savedEvent.getStartDate(),
                savedEvent.getEndDate(),
                ticketPayloads,
                Instant.now()
        );

        eventSqsPublisher.publishEventPublished(publishedEvent);

        return eventMapper.toResponse(savedEvent);
    }

    @Override
    @Transactional
    public EventResponse cancelEvent(UUID eventId, UUID userId) {
        EventEntity event = getOrganizerEventWithPermission(eventId, userId);

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ResourceConflictException("El evento ya ha sido cancelado.");
        }

        event.setStatus(EventStatus.CANCELLED);
        EventEntity savedEvent = eventRepository.save(event);

        // Construir el evento de cancelación
        EventCancelledEvent cancelledEvent = new EventCancelledEvent(
                savedEvent.getId(),
                savedEvent.getTitle(),
                savedEvent.getOrganizerId(),
                Instant.now()
        );

        // Notificar asíncronamente vía SQS (LocalStack / AWS)
        eventSqsPublisher.publishEventCancelled(cancelledEvent);

        return eventMapper.toResponse(savedEvent);
    }

    private EventEntity getOrganizerEventWithPermission(UUID eventId, UUID userId) {
        EventEntity event = eventRepository.findActiveByIdWithDetails(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado con ID: " + eventId));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> Objects.equals(a.getAuthority(), "ROLE_ADMIN") || Objects.equals(a.getAuthority(), "ADMIN"));

        if (!isAdmin && !event.getOrganizerId().equals(userId)) {
            throw new AccessDeniedException("No tienes permisos para modificar este evento.");
        }

        return event;
    }
}
