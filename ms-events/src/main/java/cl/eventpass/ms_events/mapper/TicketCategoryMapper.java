package cl.eventpass.ms_events.mapper;

import cl.eventpass.ms_events.dto.request.TicketCategoryRequest;
import cl.eventpass.ms_events.dto.response.TicketCategoryResponse;
import cl.eventpass.ms_events.entity.TicketCategoryEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class TicketCategoryMapper {

    public TicketCategoryEntity toEntity(TicketCategoryRequest request) {
        if (request == null) return null;

        return TicketCategoryEntity.builder()
                .name(request.name())
                .price(request.price())
                .totalCapacity(request.totalCapacity())
                .availableCapacity(request.totalCapacity())
                .maxPerUser(request.maxPerUser() != null ? request.maxPerUser() : 4)
                .build();
    }

    public TicketCategoryResponse toResponse(TicketCategoryEntity entity) {
        if (entity == null) return null;

        return new TicketCategoryResponse(
                entity.getId(),
                entity.getName(),
                entity.getPrice(),
                entity.getTotalCapacity(),
                entity.getAvailableCapacity(),
                entity.getMaxPerUser()
        );
    }

    public List<TicketCategoryEntity> toEntityList(List<TicketCategoryRequest> requests) {
        if (requests == null) return Collections.emptyList();
        return requests.stream()
                .map(this::toEntity)
                .toList();
    }

    public List<TicketCategoryResponse> toResponseList(List<TicketCategoryEntity> entities) {
        if (entities == null) return Collections.emptyList();
        return entities.stream()
                .map(this::toResponse)
                .toList();
    }
}
