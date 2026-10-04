package cl.eventpass.ms_events.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(
    name = "venues",
    indexes = {
        @Index(name = "idx_venue_city", columnList = "city"),
        @Index(name = "idx_venue_place_id", columnList = "place_id")
    }
)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Getter @Setter
public class VenueEntity extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "country", nullable = false, length = 100)
    @Builder.Default
    private String country = "Chile";

    // Coordenadas geoespaciales
    @Column(name = "latitude", precision = 11, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "place_id", length = 255)
    private String placeId;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "image_url")
    private String imageUrl;
}
