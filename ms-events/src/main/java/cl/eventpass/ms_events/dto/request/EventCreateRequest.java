package cl.eventpass.ms_events.dto.request;

import cl.eventpass.ms_events.entity.TicketCategoryEntity;
import cl.eventpass.ms_events.enums.EventCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        List<TicketCategoryEntity> ticketCategories
) {
}
