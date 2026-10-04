package cl.eventpass.ms_events.controller.docs;

import cl.eventpass.ms_events.dto.request.CapacityReleaseRequest;
import cl.eventpass.ms_events.dto.request.CapacityReservationRequest;
import cl.eventpass.ms_events.dto.request.EventCreateRequest;
import cl.eventpass.ms_events.dto.request.EventUpdateRequest;
import cl.eventpass.ms_events.dto.response.CapacityReleaseResponse;
import cl.eventpass.ms_events.dto.response.CapacityReservationResponse;
import cl.eventpass.ms_events.dto.response.EventResponse;
import cl.eventpass.ms_events.dto.response.StandardErrorResponse;
import cl.eventpass.ms_events.dto.response.StandardResponse;
import cl.eventpass.ms_events.enums.EventCategory;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Tag(
        name = "Events",
        description = "Endpoints para la creación, consulta, actualización y gestión del estado de eventos."
)
public interface EventsControllerDocs {

    @Operation(
            summary = "Crear evento",
            description = "Crea un nuevo evento asociado al organizador autenticado. "
                    + "El recinto indicado debe existir y encontrarse activo. "
                    + "La capacidad total de las categorías de entradas no puede superar "
                    + "la capacidad máxima del recinto."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Evento creado exitosamente."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Los datos enviados no cumplen las validaciones requeridas, "
                            + "la fecha de término es anterior a la fecha de inicio "
                            + "o la capacidad total de entradas supera el aforo del recinto.",
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
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No se encontró el recinto indicado o el recinto no se encuentra activo.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<EventResponse>> createEvent(
            @RequestBody
            EventCreateRequest request,

            @Parameter(hidden = true)
            UUID organizerId
    );


    @Operation(
            summary = "Obtener evento por ID",
            description = "Obtiene la información de un evento específico utilizando su identificador único. "
                    + "El evento debe existir y no encontrarse eliminado."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Evento encontrado exitosamente."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No se encontró un evento activo asociado al identificador proporcionado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<EventResponse>> getEventById(
            @Parameter(
                    description = "Identificador único del evento.",
                    required = true
            )
            @PathVariable
            UUID id
    );


    @Operation(
            summary = "Obtener catálogo de eventos publicados",
            description = "Obtiene una lista paginada de eventos publicados. "
                    + "Opcionalmente permite filtrar los resultados por categoría. "
                    + "Por defecto se retornan 10 eventos por página, ordenados por fecha de inicio "
                    + "de forma ascendente."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Catálogo de eventos obtenido exitosamente."
            )
    })
    ResponseEntity<StandardResponse<Page<EventResponse>>> getPublishedEvents(
            @Parameter(
                    description = "Categoría por la cual filtrar los eventos. "
                            + "Si no se proporciona, se retornan eventos publicados de todas las categorías.",
                    required = false
            )
            @RequestParam(required = false)
            EventCategory category,

            @PageableDefault(
                    size = 10,
                    sort = "startDate",
                    direction = Sort.Direction.ASC
            )
            Pageable pageable
    );


    @Operation(
            summary = "Obtener eventos del organizador autenticado",
            description = "Obtiene una lista paginada de los eventos asociados al organizador autenticado. "
                    + "Los resultados corresponden al usuario identificado mediante el token JWT. "
                    + "Por defecto se retornan 10 eventos por página, ordenados por fecha de creación "
                    + "de forma descendente."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de eventos del organizador obtenida exitosamente."
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
    ResponseEntity<StandardResponse<Page<EventResponse>>> getOrganizerEvents(
            @Parameter(hidden = true)
            UUID organizerId,

            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    );


    @Operation(
            summary = "Actualizar evento",
            description = "Actualiza la información de un evento existente. "
                    + "El usuario debe ser ADMIN o el organizador propietario del evento. "
                    + "No es posible modificar un evento que se encuentre cancelado. "
                    + "Si se cambia el recinto, este debe existir, encontrarse activo y disponer "
                    + "de capacidad suficiente para las entradas actualmente configuradas."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Evento actualizado exitosamente."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Los datos enviados no cumplen las validaciones requeridas, "
                            + "la fecha de término es anterior a la fecha de inicio, "
                            + "el evento está cancelado o la capacidad del nuevo recinto "
                            + "es insuficiente para las entradas configuradas.",
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
                    description = "El usuario autenticado no posee los roles requeridos "
                            + "o no tiene permisos sobre el evento solicitado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No se encontró el evento solicitado o el nuevo recinto indicado "
                            + "no existe o no se encuentra activo.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<EventResponse>> updateEvent(
            @Parameter(
                    description = "Identificador único del evento.",
                    required = true
            )
            @PathVariable
            UUID id,

            @RequestBody
            EventUpdateRequest request,

            @Parameter(hidden = true)
            UUID userId
    );


    @Operation(
            summary = "Publicar evento",
            description = "Cambia el estado del evento a PUBLISHED. "
                    + "El usuario debe ser ADMIN o el organizador propietario del evento. "
                    + "Un evento que ya se encuentre publicado no puede volver a publicarse."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Evento publicado exitosamente."
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
                    description = "El usuario autenticado no posee los roles requeridos "
                            + "o no tiene permisos sobre el evento solicitado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No se encontró el evento solicitado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "El evento ya se encuentra publicado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<EventResponse>> publishEvent(
            @Parameter(
                    description = "Identificador único del evento.",
                    required = true
            )
            @PathVariable
            UUID id,

            @Parameter(hidden = true)
            UUID userId
    );


    @Operation(
            summary = "Cancelar evento",
            description = "Cambia el estado del evento a CANCELLED. "
                    + "El usuario debe ser ADMIN o el organizador propietario del evento. "
                    + "Un evento que ya se encuentre cancelado no puede volver a cancelarse."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Evento cancelado exitosamente."
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
                    description = "El usuario autenticado no posee los roles requeridos "
                            + "o no tiene permisos sobre el evento solicitado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No se encontró el evento solicitado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "El evento ya se encuentra cancelado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<EventResponse>> cancelEvent(
            @Parameter(
                    description = "Identificador único del evento.",
                    required = true
            )
            @PathVariable
            UUID id,

            @Parameter(hidden = true)
            UUID userId
    );


    @Operation(
            summary = "Reservar capacidad de entradas",
            description = "Reserva capacidad disponible de una categoría de tickets "
                    + "para un evento. Este endpoint es de uso interno entre microservicios "
                    + "y debe ser invocado utilizando un Service JWT con el rol "
                    + "ROLE_INTERNAL_SERVICE."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Capacidad reservada exitosamente."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "La cantidad solicitada no es válida.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No existe un Service JWT válido, el token ha expirado o no fue proporcionado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "El token autenticado no posee el rol ROLE_INTERNAL_SERVICE.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No se encontró el evento o la categoría de ticket indicada.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "No existe suficiente capacidad disponible para realizar la reserva.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<CapacityReservationResponse>> reserveCapacity(
            @Parameter(
                    description = "Identificador único del evento.",
                    required = true
            )
            @PathVariable
            UUID eventId,

            @Parameter(
                    description = "Datos necesarios para reservar capacidad.",
                    required = true
            )
            @RequestBody
            CapacityReservationRequest request
    );


    @Operation(
            summary = "Liberar capacidad de entradas",
            description = "Libera capacidad previamente reservada de una categoría "
                    + "de tickets para un evento. Este endpoint es de uso interno "
                    + "entre microservicios y debe ser invocado utilizando un Service JWT "
                    + "con el rol ROLE_INTERNAL_SERVICE."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Capacidad liberada exitosamente."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "La cantidad solicitada no es válida.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No existe un Service JWT válido, el token ha expirado o no fue proporcionado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "El token autenticado no posee el rol ROLE_INTERNAL_SERVICE.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No se encontró el evento o la categoría de ticket indicada.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "La capacidad disponible no permite realizar la liberación solicitada.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<CapacityReleaseResponse>> releaseCapacity(
            @Parameter(
                    description = "Identificador único del evento.",
                    required = true
            )
            @PathVariable
            UUID eventId,

            @Parameter(
                    description = "Datos necesarios para liberar capacidad.",
                    required = true
            )
            @RequestBody
            CapacityReleaseRequest request
    );
}
