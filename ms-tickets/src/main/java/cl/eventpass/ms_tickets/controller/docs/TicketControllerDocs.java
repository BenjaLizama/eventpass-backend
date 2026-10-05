package cl.eventpass.ms_tickets.controller.docs;

import cl.eventpass.ms_tickets.dto.response.StandardErrorResponse;
import cl.eventpass.ms_tickets.dto.response.StandardResponse;
import cl.eventpass.ms_tickets.dto.response.TicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "Tickets",
        description = "Endpoints relacionados con la gestión y consulta de tickets."
)
public interface TicketControllerDocs {

    @Operation(
            summary = "Obtener mis tickets",
            description = """
                    Obtiene todos los tickets pertenecientes al usuario autenticado.

                    El usuario se identifica mediante el JWT enviado en el encabezado
                    Authorization. El userId no debe enviarse como parámetro de la petición.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tickets obtenidos correctamente.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = StandardResponse.class
                            ),
                            examples = @ExampleObject(
                                    name = "Tickets encontrados",
                                    value = """
                                            {
                                              "status": 200,
                                              "message": "Recursos obtenidos con exito.",
                                              "data": [
                                                {
                                                  "id": "6c8d9a5b-5e45-4d4f-9c1e-2f7f3d6a8b10",
                                                  "orderId": "b8a2f3e1-5f7c-4b91-a2c3-8d7e6f5a4b32",
                                                  "orderItemId": "1f3c7e9a-2b5d-4a8f-b6c1-9e7d5a3b2f10",
                                                  "ticketIndex": 0,
                                                  "userId": "b5054da9-f901-4912-bc05-ce0f4ac0ba24",
                                                  "eventId": "d1e2f3a4-b5c6-4789-9012-3456789abcde",
                                                  "ticketCategoryId": "a1b2c3d4-e5f6-4789-9012-3456789abcdef",
                                                  "ticketCode": "550e8400-e29b-41d4-a716-446655440000",
                                                  "status": "ACTIVE",
                                                  "usedAt": null,
                                                  "createdAt": "2026-10-05T21:30:00Z"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Usuario no autenticado.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor."
            )
    })
    ResponseEntity<StandardResponse<List<TicketResponse>>> getMyTickets(
            @Parameter(hidden = true)
            UUID userId
    );
}
