package cl.eventpass.ms_events.dto.request;

import cl.eventpass.ms_events.dto.response.TicketCategoryResponse;
import cl.eventpass.ms_events.entity.TicketCategoryEntity;
import cl.eventpass.ms_events.enums.EventCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@io.swagger.v3.oas.annotations.media.Schema(description = "Sustituye venueId por el ID de un recinto y usa fechas futuras.", example = "{\"title\":\"Concierto Demo\",\"description\":\"Evento de prueba\",\"category\":\"CONCERT\",\"startDate\":\"2030-11-20T19:00:00Z\",\"endDate\":\"2030-11-20T21:00:00Z\",\"venueId\":\"123e4567-e89b-42d3-a456-426614174000\",\"ticketCategories\":[{\"name\":\"General\",\"price\":10000,\"totalCapacity\":10,\"maxPerUser\":2}]}")
public record EventCreateRequest(
        @NotBlank(message = "El título del evento es obligatorio")
        @Size(max = 200, message = "El título no puede superar los 200 caracteres")
        String title,

        String description,

        @NotNull(message = "La categoría es obligatoria")
        EventCategory category,

        @NotNull(message = "La fecha de inicio es obligatoria")
        @Future(message = "La fecha de inicio debe ser en el futuro")
        Instant startDate,

        @NotNull(message = "La fecha de término es obligatoria")
        Instant endDate,

        String bannerUrl,

        @NotNull(message = "El ID del recinto es obligatorio")
        UUID venueId,

        @NotEmpty(message = "Debe incluir al menos una categoría de entradas")
        @Valid
        List<TicketCategoryRequest> ticketCategories
) {
}
