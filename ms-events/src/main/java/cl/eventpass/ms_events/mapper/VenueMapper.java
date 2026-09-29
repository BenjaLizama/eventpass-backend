package cl.eventpass.ms_events.mapper;

import cl.eventpass.ms_events.dto.request.VenueRequest;
import cl.eventpass.ms_events.dto.response.VenueResponse;
import cl.eventpass.ms_events.entity.VenueEntity;
import org.springframework.stereotype.Component;

@Component
public class VenueMapper {

    public VenueEntity toEntity(VenueRequest request) {
        if (request == null) return null;

        return VenueEntity.builder()
                .name(request.name())
                .address(request.address())
                .city(request.city())
                .country(request.country())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .placeId(request.placeId())
                .capacity(request.capacity())
                .imageUrl(request.imageUrl())
                .build();
    }

    public VenueResponse toResponse(VenueEntity entity) {
        if (entity == null) return null;

        return new VenueResponse(
                entity.getId(),
                entity.getName(),
                entity.getAddress(),
                entity.getCity(),
                entity.getCountry(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getPlaceId(),
                entity.getCapacity(),
                entity.getImageUrl(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public void updateEntity(VenueEntity entity, VenueRequest request) {
        if (entity == null || request == null) return;

        entity.setName(request.name());
        entity.setAddress(request.address());
        entity.setCity(request.city());
        if (request.country() != null) {
            entity.setCountry(request.country());
        }
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setPlaceId(request.placeId());
        entity.setCapacity(request.capacity());
        entity.setImageUrl(request.imageUrl());
    }
}
