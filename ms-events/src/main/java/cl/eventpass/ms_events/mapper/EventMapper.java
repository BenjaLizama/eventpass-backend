package cl.eventpass.ms_events.mapper;

import cl.eventpass.ms_events.dto.request.EventCreateRequest;
import cl.eventpass.ms_events.dto.response.EventResponse;
import cl.eventpass.ms_events.entity.EventEntity;
import cl.eventpass.ms_events.entity.TicketCategoryEntity;
import cl.eventpass.ms_events.entity.VenueEntity;
import cl.eventpass.ms_events.enums.EventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EventMapper {

    private final VenueMapper venueMapper;
    private final TicketCategoryMapper ticketCategoryMapper;

    public EventEntity toEntity(
            EventCreateRequest request,
            VenueEntity venue,
            UUID organizerId
    ) {
        if (request == null) {
            return null;
        }

        EventEntity event = EventEntity.builder()
                .organizerId(organizerId)
                .title(request.title())
                .description(request.description())
                .category(request.category())
                .status(EventStatus.DRAFT)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .bannerUrl(request.bannerUrl())
                .venue(venue)
                .build();

        if (request.ticketCategories() != null) {
            List<TicketCategoryEntity> categories =
                    ticketCategoryMapper.toEntityList(request.ticketCategories());

            categories.forEach(event::addTicketCategory);
        }

        return event;
    }

    public EventResponse toResponse(EventEntity entity) {
        if (entity == null) {
            return null;
        }

        return new EventResponse(
                entity.getId(),
                entity.getOrganizerId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getStatus(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getBannerUrl(),
                venueMapper.toResponse(entity.getVenue()),
                ticketCategoryMapper.toResponseList(entity.getTicketCategories()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
