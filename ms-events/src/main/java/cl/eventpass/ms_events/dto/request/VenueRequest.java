package cl.eventpass.ms_events.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record VenueRequest(
        @NotBlank(message = "El nombre del recinto es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String name,

        @NotBlank(message = "La dirección es obligatoria")
        @Size(max = 255, message = "La dirección no puede superar los 255 caracteres")
        String address,

        @NotBlank(message = "La ciudad es obligatoria")
        @Size(max = 100, message = "La ciudad no puede superar los 100 caracteres")
        String city,

        @Size(max = 100, message = "El país no puede superar los 100 caracteres")
        String country,

        @DecimalMin(value = "-90.0", message = "La latitud debe ser mayor o igual a -90")
        @DecimalMax(value = "90.0", message = "La latitud debe ser menor o igual a 90")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "La longitud debe ser mayor o igual a -180")
        @DecimalMax(value = "180.0", message = "La longitud debe ser menor o igual a 180")
        BigDecimal longitude,

        String placeId,

        @NotNull(message = "La capacidad es obligatoria")
        @Min(value = 1, message = "La capacidad mínima debe ser de al menos 1 persona")
        Integer capacity,

        String imageUrl
) {
}
