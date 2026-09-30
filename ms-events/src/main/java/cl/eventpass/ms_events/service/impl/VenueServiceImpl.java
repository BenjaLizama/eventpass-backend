package cl.eventpass.ms_events.service.impl;

import cl.eventpass.ms_events.dto.request.VenueRequest;
import cl.eventpass.ms_events.dto.response.VenueResponse;
import cl.eventpass.ms_events.entity.VenueEntity;
import cl.eventpass.ms_events.exception.ResourceNotFoundException;
import cl.eventpass.ms_events.mapper.VenueMapper;
import cl.eventpass.ms_events.repository.VenueRepository;
import cl.eventpass.ms_events.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;
    private final VenueMapper venueMapper;

    @Override
    @Transactional
    public VenueResponse createVenue(VenueRequest request) {
        VenueEntity entity = venueMapper.toEntity(request);
        VenueEntity savedEntity = venueRepository.save(entity);

        return venueMapper.toResponse(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public VenueResponse getVenueById(UUID id) {
        VenueEntity entity = venueRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recinto no encontrado con ID: " + id));

        return venueMapper.toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VenueResponse> getAllVenues(Pageable pageable) {
        return venueRepository.findByDeletedAtIsNull(pageable)
                .map(venueMapper::toResponse);
    }

    @Override
    @Transactional
    public VenueResponse updateVenue(UUID id, VenueRequest request) {
        VenueEntity entity = venueRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recinto no encontrado con ID: " + id));

        venueMapper.updateEntity(entity, request);
        VenueEntity updatedEntity = venueRepository.save(entity);
        return venueMapper.toResponse(updatedEntity);
    }

    @Override
    @Transactional
    public void deleteVenue(UUID id) {
        VenueEntity entity = venueRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recinto no encontrado con ID: " + id));

        entity.setDeletedAt(Instant.now());
        venueRepository.save(entity);
    }
}
