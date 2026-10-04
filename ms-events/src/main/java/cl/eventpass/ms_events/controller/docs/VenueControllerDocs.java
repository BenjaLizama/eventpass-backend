package cl.eventpass.ms_events.controller.docs;

import cl.eventpass.ms_events.dto.request.VenueRequest;
import cl.eventpass.ms_events.dto.response.StandardErrorResponse;
import cl.eventpass.ms_events.dto.response.StandardResponse;
import cl.eventpass.ms_events.dto.response.VenueResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@Tag(
        name = "Venues",
        description = "Endpoints para la creación, consulta, actualización y eliminación de recintos."
)
public interface VenueControllerDocs {

    @Operation(
            summary = "Crear recinto",
            description = "Crea un nuevo recinto dentro del sistema. "
                    + "La operación requiere autenticación y puede ser ejecutada por usuarios "
                    + "con los roles ADMIN u ORGANIZER."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Recinto creado exitosamente."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Los datos enviados no cumplen las validaciones requeridas.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No existe una autenticación válida, el token ha expirado o no fue proporcionado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "El usuario autenticado no posee los roles ADMIN u ORGANIZER.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<VenueResponse>> createVenue(
            @RequestBody
            VenueRequest request
    );


    @Operation(
            summary = "Obtener recinto por ID",
            description = "Obtiene la información de un recinto activo utilizando su identificador único. "
                    + "Los recintos eliminados lógicamente no son considerados."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Recinto encontrado exitosamente."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No se encontró un recinto activo asociado al identificador proporcionado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<VenueResponse>> getVenueById(
            @Parameter(
                    description = "Identificador único del recinto.",
                    required = true
            )
            @PathVariable
            UUID id
    );


    @Operation(
            summary = "Obtener todos los recintos",
            description = "Obtiene una lista paginada de todos los recintos activos. "
                    + "Los recintos eliminados lógicamente son excluidos de los resultados. "
                    + "Por defecto se retornan 10 registros por página y los resultados "
                    + "se ordenan por nombre de forma ascendente."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de recintos obtenida exitosamente."
            )
    })
    ResponseEntity<StandardResponse<Page<VenueResponse>>> getAllVenues(
            @PageableDefault(
                    size = 10,
                    sort = "name"
            )
            Pageable pageable
    );


    @Operation(
            summary = "Actualizar recinto",
            description = "Actualiza la información de un recinto existente. "
                    + "La operación requiere autenticación y permisos de administrador. "
                    + "El recinto debe existir y encontrarse activo."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Recinto actualizado exitosamente."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Los datos enviados no cumplen las validaciones requeridas.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No existe una autenticación válida, el token ha expirado o no fue proporcionado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "El usuario autenticado no posee permisos de administrador.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No se encontró un recinto activo asociado al identificador proporcionado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<VenueResponse>> updateVenue(
            @Parameter(
                    description = "Identificador único del recinto.",
                    required = true
            )
            @PathVariable
            UUID id,

            @RequestBody
            VenueRequest request
    );


    @Operation(
            summary = "Eliminar recinto",
            description = "Elimina lógicamente un recinto existente. "
                    + "La operación requiere autenticación y permisos de administrador. "
                    + "El recinto no es eliminado físicamente de la base de datos, "
                    + "sino que se registra la fecha de eliminación."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Recinto eliminado exitosamente."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No existe una autenticación válida, el token ha expirado o no fue proporcionado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "El usuario autenticado no posee permisos de administrador.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No se encontró un recinto activo asociado al identificador proporcionado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<Void>> deleteVenue(
            @Parameter(
                    description = "Identificador único del recinto.",
                    required = true
            )
            @PathVariable
            UUID id
    );
}
