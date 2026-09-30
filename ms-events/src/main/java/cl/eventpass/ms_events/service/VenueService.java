package cl.eventpass.ms_events.service;

import cl.eventpass.ms_events.dto.request.VenueRequest;
import cl.eventpass.ms_events.dto.response.VenueResponse;
import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface VenueService {
    VenueResponse createVenue(VenueRequest request);
    VenueResponse getVenueById(UUID id);
    Page<VenueResponse> getAllVenues(Pageable pageable);
    VenueResponse updateVenue(UUID id, VenueRequest request);
    void deleteVenue(UUID id);
}
