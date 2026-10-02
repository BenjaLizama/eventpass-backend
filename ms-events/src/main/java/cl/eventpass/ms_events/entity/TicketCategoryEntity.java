package cl.eventpass.ms_events.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(
    name = "ticket_categories",
    indexes = {
        @Index(name = "idx_ticket_category_event_id", columnList = "event_id")
    }
)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Getter @Setter
public class TicketCategoryEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private EventEntity event;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "total_capacity", nullable = false)
    private Integer totalCapacity;

    @Column(name = "available_capacity", nullable = false)
    private Integer availableCapacity;

    @Column(name = "max_per_user", nullable = false)
    @Builder.Default
    private Integer maxPerUser = 4;
}
