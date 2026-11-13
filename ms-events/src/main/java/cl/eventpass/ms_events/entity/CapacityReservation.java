package cl.eventpass.ms_events.entity;

import cl.eventpass.ms_events.enums.CapacityReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "capacity_reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CapacityReservation {

    @Id
    @Column(name = "order_item_id")
    private UUID orderItemId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "ticket_category_id", nullable = false)
    private UUID ticketCategoryId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CapacityReservationStatus status;

    public void markReserved() {
        status = CapacityReservationStatus.RESERVED;
    }

    public void markReleased() {
        status = CapacityReservationStatus.RELEASED;
    }
}
