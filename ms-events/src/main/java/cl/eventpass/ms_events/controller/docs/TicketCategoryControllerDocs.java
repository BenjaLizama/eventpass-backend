package cl.eventpass.ms_events.controller.docs;

import cl.eventpass.ms_events.dto.response.StandardErrorResponse;
import cl.eventpass.ms_events.dto.response.StandardResponse;
import cl.eventpass.ms_events.dto.response.TicketCategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@Tag(
        name = "Ticket Categories",
        description = "Endpoints para la consulta de categorías de entradas asociadas a eventos."
)
public interface TicketCategoryControllerDocs {

    @Operation(
            summary = "Obtener categoría de ticket",
            description = "Obtiene la información de una categoría de ticket específica "
                    + "asociada a un evento. La categoría debe existir, no encontrarse eliminada "
                    + "y pertenecer al evento indicado."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Categoría de ticket encontrada exitosamente."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No se encontró la categoría de ticket solicitada "
                            + "o la categoría no pertenece al evento indicado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<TicketCategoryResponse>> getTicketCategory(
            @Parameter(
                    description = "Identificador único del evento.",
                    required = true
            )
            @PathVariable
            UUID eventId,

            @Parameter(
                    description = "Identificador único de la categoría de ticket.",
                    required = true
            )
            @PathVariable
            UUID ticketCategoryId
    );
}
