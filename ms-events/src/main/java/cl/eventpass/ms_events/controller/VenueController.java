package cl.eventpass.ms_events.controller;

import cl.eventpass.ms_events.controller.docs.VenueControllerDocs;
import cl.eventpass.ms_events.dto.request.VenueRequest;
import cl.eventpass.ms_events.dto.response.StandardResponse;
import cl.eventpass.ms_events.dto.response.VenueResponse;
import cl.eventpass.ms_events.service.VenueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
public class VenueController implements VenueControllerDocs {

    private final VenueService venueService;

    @Override
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public ResponseEntity<StandardResponse<VenueResponse>> createVenue(@Valid @RequestBody VenueRequest request) {
        VenueResponse response = venueService.createVenue(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(StandardResponse.created(
                        "Recinto creado con éxito.",
                        response
                ));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<StandardResponse<VenueResponse>> getVenueById(@PathVariable UUID id) {
        VenueResponse response = venueService.getVenueById(id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(StandardResponse.ok(
                        "Recinto encontrado con éxito.",
                        response
                ));
    }

    @Override
    @GetMapping
    public ResponseEntity<StandardResponse<Page<VenueResponse>>> getAllVenues(
            @PageableDefault(size = 10, sort = "name") Pageable pageable
    ) {
        Page<VenueResponse> response = venueService.getAllVenues(pageable);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(StandardResponse.ok(
                        "Lista de recintos obtenida con éxito.",
                        response
                ));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<VenueResponse>> updateVenue(
            @PathVariable UUID id,
            @Valid @RequestBody VenueRequest request
    ) {
        VenueResponse response = venueService.updateVenue(id, request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(StandardResponse.ok(
                        "Recinto actualizado con éxito.",
                        response
                ));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<Void>> deleteVenue(@PathVariable UUID id) {
        venueService.deleteVenue(id);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(StandardResponse.ok(
                        "Recinto eliminado con éxito.",
                        null
                ));
    }
}
