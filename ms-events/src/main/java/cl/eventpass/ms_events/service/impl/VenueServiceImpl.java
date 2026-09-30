package cl.eventpass.ms_events.service.impl;

import cl.eventpass.ms_events.dto.request.VenueRequest;
import cl.eventpass.ms_events.dto.response.VenueResponse;
import cl.eventpass.ms_events.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.Page;
import org.springframework.stereotype.Service;

import java.awt.print.Pageable;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {

    @Override
    public VenueResponse createVenue(VenueRequest request) {
        return null;
    }

    @Override
    public VenueResponse getVenueById(UUID id) {
        return null;
    }

    @Override
    public Page getAllVenues(Pageable pageable) {
        return null;
    }

    @Override
    public VenueResponse updateVenue(UUID id, VenueRequest request) {
        return null;
    }

    @Override
    public void deleteVenue(UUID id) {

    }
}
