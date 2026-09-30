package cl.eventpass.ms_events.controller;

import cl.eventpass.ms_events.config.annotation.CurrentUserId;
import cl.eventpass.ms_events.controller.docs.EventsControllerDocs;
import cl.eventpass.ms_events.dto.request.EventCreateRequest;
import cl.eventpass.ms_events.dto.request.EventUpdateRequest;
import cl.eventpass.ms_events.dto.response.EventResponse;
import cl.eventpass.ms_events.dto.response.StandardResponse;
import cl.eventpass.ms_events.enums.EventCategory;
import cl.eventpass.ms_events.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventsController implements EventsControllerDocs {

    private final EventService eventService;

    @Override
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<StandardResponse<EventResponse>> createEvent(
            @Valid @RequestBody EventCreateRequest request,
            @CurrentUserId UUID organizerId
    ) {
        EventResponse response = eventService.createEvent(request, organizerId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(StandardResponse.created(
                        "Evento creado con éxito.",
                        response
                ));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<StandardResponse<EventResponse>> getEventById(@PathVariable UUID id) {
        EventResponse response = eventService.getEventById(id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(StandardResponse.ok(
                        "Evento encontrado con éxito.",
                        response
                ));
    }

    @Override
    @GetMapping
    public ResponseEntity<StandardResponse<Page<EventResponse>>> getPublishedEvents(
            @RequestParam(required = false) EventCategory category,
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<EventResponse> response = eventService.getPublishedEvents(category, pageable);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(StandardResponse.ok(
                        "Catálogo de eventos publicados obtenido con éxito.",
                        response
                ));
    }

    @Override
    @GetMapping("/my-events")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<StandardResponse<Page<EventResponse>>> getOrganizerEvents(
            @CurrentUserId UUID organizerId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<EventResponse> response = eventService.getOrganizerEvents(organizerId, pageable);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(StandardResponse.ok(
                        "Lista de mis eventos obtenida con éxito.",
                        response
                ));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<StandardResponse<EventResponse>> updateEvent(
            @PathVariable UUID id,
            @Valid @RequestBody EventUpdateRequest request,
            @CurrentUserId UUID userId
    ) {
        EventResponse response = eventService.updateEvent(id, request, userId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(StandardResponse.ok(
                        "Evento actualizado con éxito.",
                        response
                ));
    }

    @Override
    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<StandardResponse<EventResponse>> publishEvent(
            @PathVariable UUID id,
            @CurrentUserId UUID userId
    ) {
        EventResponse response = eventService.publishEvent(id, userId);
        return ResponseEntity
                .ok(StandardResponse.ok(
                        "Evento publicado con éxito.",
                        response
                ));
    }

    @Override
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<StandardResponse<EventResponse>> cancelEvent(
            @PathVariable UUID id,
            @CurrentUserId UUID userId
    ) {
        EventResponse response = eventService.cancelEvent(id, userId);
        return ResponseEntity
                .ok(StandardResponse.ok(
                        "Evento cancelado con éxito.",
                        response
                ));
    }
}
