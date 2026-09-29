package cl.eventpass.ms_events.entity;

import cl.eventpass.ms_events.enums.EventCategory;
import cl.eventpass.ms_events.enums.EventStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "events",
    indexes = {
        @Index(name = "idx_event_status_start_date", columnList = "status, start_date"),
        @Index(name = "idx_event_organizer_id", columnList = "organizer_id"),
        @Index(name = "idx_event_category", columnList = "category")
    }
)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Getter @Setter
public class EventEntity extends BaseEntity {

    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private EventCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date", nullable = false)
    private Instant endDate;

    @Column(name = "banner_url")
    private String bannerUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private VenueEntity venue;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TicketCategoryEntity> ticketCategories = new ArrayList<>();

    public void addTicketCategory(TicketCategoryEntity category) {
        ticketCategories.add(category);
        category.setEvent(this);
    }
}
