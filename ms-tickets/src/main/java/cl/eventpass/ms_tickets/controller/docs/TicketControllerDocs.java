package cl.eventpass.ms_tickets.controller.docs;

import cl.eventpass.ms_tickets.dto.response.StandardErrorResponse;
import cl.eventpass.ms_tickets.dto.response.StandardResponse;
import cl.eventpass.ms_tickets.dto.response.TicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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

    @Operation(
            summary = "Obtener ticket por código",
            description = """
                    Obtiene un ticket específico mediante su código único.

                    El usuario se identifica mediante el JWT enviado en el encabezado
                    Authorization. El ticket debe pertenecer al usuario autenticado.

                    Si el ticket no existe o pertenece a otro usuario, se responde con
                    HTTP 404 para evitar revelar información sobre tickets de otros usuarios.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ticket obtenido correctamente.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = StandardResponse.class
                            ),
                            examples = @ExampleObject(
                                    name = "Ticket encontrado",
                                    value = """
                                            {
                                              "status": 200,
                                              "message": "Ticket obtenido con éxito.",
                                              "data": {
                                                "id": "0bda2d9a-127b-4053-b693-f5a48d10a6c2",
                                                "orderId": "21206d68-072b-4c42-b12e-7fe18c220789",
                                                "orderItemId": "3475d378-62d8-4940-a9c7-53539ce5b777",
                                                "ticketIndex": 0,
                                                "userId": "b8b4d0e0-aabb-4257-b18d-50d98ad5492c",
                                                "eventId": "ef5468a5-56f5-4d6e-82e5-b4c1531f1970",
                                                "ticketCategoryId": "6db93a7e-41ef-4899-8723-e3f8ea73cdea",
                                                "ticketCode": "1a2ba7b8-e9b9-42ac-8e5f-ca384e608d39",
                                                "status": "ACTIVE",
                                                "usedAt": null,
                                                "createdAt": "2026-10-05T23:04:50.846Z"
                                              }
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
                    responseCode = "404",
                    description = "Ticket no encontrado o no pertenece al usuario autenticado.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<TicketResponse>> getTicketByCode(

            @Parameter(
                    name = "ticketCode",
                    description = "Código único del ticket.",
                    required = true,
                    example = "1a2ba7b8-e9b9-42ac-8e5f-ca384e608d39",
                    in = ParameterIn.PATH
            )
            String ticketCode,

            @Parameter(hidden = true)
            UUID userId
    );

    @Operation(
            summary = "Utilizar ticket",
            description = """
                Marca un ticket como utilizado.

                Esta operación está restringida a usuarios con rol STAFF.
                El ticket debe encontrarse en estado ACTIVE.

                Si el ticket ya fue utilizado o se encuentra cancelado,
                la operación será rechazada.
                """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ticket utilizado correctamente.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = StandardResponse.class
                            ),
                            examples = @ExampleObject(
                                    name = "Ticket utilizado",
                                    value = """
                                        {
                                          "status": 200,
                                          "message": "Ticket utilizado con éxito.",
                                          "data": {
                                            "id": "0bda2d9a-127b-4053-b693-f5a48d10a6c2",
                                            "orderId": "21206d68-072b-4c42-b12e-7fe18c220789",
                                            "orderItemId": "3475d378-62d8-4940-a9c7-53539ce5b777",
                                            "ticketIndex": 0,
                                            "userId": "b8b4d0e0-aabb-4257-b18d-50d98ad5492c",
                                            "eventId": "ef5468a5-56f5-4d6e-82e5-b4c1531f1970",
                                            "ticketCategoryId": "6db93a7e-41ef-4899-8723-e3f8ea73cdea",
                                            "ticketCode": "1a2ba7b8-e9b9-42ac-8e5f-ca384e608d39",
                                            "status": "USED",
                                            "usedAt": "2026-10-06T00:30:00Z",
                                            "createdAt": "2026-10-05T23:04:50.846Z"
                                          }
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
                    responseCode = "403",
                    description = "El usuario autenticado no posee el rol STAFF.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ticket no encontrado.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "El ticket no puede utilizarse debido a su estado actual.",
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
    ResponseEntity<StandardResponse<TicketResponse>> useTicket(

            @Parameter(
                    name = "ticketCode",
                    description = "Código único del ticket que será utilizado.",
                    required = true,
                    example = "1a2ba7b8-e9b9-42ac-8e5f-ca384e608d39",
                    in = ParameterIn.PATH
            )
            String ticketCode
    );
}
