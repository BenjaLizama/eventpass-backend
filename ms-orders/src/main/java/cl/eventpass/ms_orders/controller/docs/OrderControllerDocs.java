package cl.eventpass.ms_orders.controller.docs;

import cl.eventpass.ms_orders.dto.request.OrderCreateRequest;
import cl.eventpass.ms_orders.dto.response.OrderResponse;
import cl.eventpass.ms_orders.dto.response.StandardErrorResponse;
import cl.eventpass.ms_orders.dto.response.StandardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@Tag(
        name = "Orders",
        description = "Endpoints para la creación y gestión de órdenes de compra."
)
@SecurityRequirement(name = "bearerAuth")
public interface OrderControllerDocs {

    @Operation(
            summary = "Crear orden",
            description = """
                    Crea una nueva orden de compra para una categoría de ticket.

                    El usuario autenticado se identifica mediante el JWT enviado
                    en la solicitud. La identidad del usuario se obtiene mediante
                    @CurrentUserId.

                    Antes de persistir la orden, ms-orders consulta la categoría
                    de ticket en ms-events, valida el límite de compra por usuario
                    y solicita la reserva del aforo mediante autenticación
                    interna entre microservicios.

                    Si la reserva de aforo es exitosa, se crea la orden en estado
                    PENDING y con estado de pago PENDING.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Orden creada exitosamente."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Los datos de la solicitud son inválidos o la cantidad solicitada supera el máximo permitido por usuario.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "El usuario no está autenticado o el token JWT no es válido.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No se encontró el evento o la categoría de ticket solicitada.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "No existe suficiente aforo disponible para completar la reserva.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Ocurrió un error interno al procesar la creación de la orden.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<OrderResponse>> createOrder(
            @Parameter(
                    description = "Datos necesarios para crear la orden.",
                    required = true
            )
            @Valid
            @RequestBody OrderCreateRequest request,

            @Parameter(
                    description = "UUID del usuario autenticado obtenido desde el JWT.",
                    hidden = true
            )
            UUID userId
    );

    @Operation(
            summary = "Obtener orden por ID",
            description = """
                    Obtiene una orden de compra mediante su identificador único.

                    La orden solo puede ser consultada por el usuario autenticado
                    al que pertenece. La identidad del usuario se obtiene mediante
                    @CurrentUserId a partir del JWT.

                    Si la orden no existe o no pertenece al usuario autenticado,
                    se retorna un error 404.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Orden obtenida exitosamente."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "El usuario no está autenticado o el token JWT no es válido.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No se encontró la orden solicitada.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<OrderResponse>> getOrderById(
            @Parameter(
                    description = "Identificador único de la orden.",
                    required = true
            )
            UUID id,

            @Parameter(
                    description = "UUID del usuario autenticado obtenido desde el JWT.",
                    hidden = true
            )
            UUID userId
    );

    @Operation(
            summary = "Obtener mis órdenes",
            description = """
                    Obtiene las órdenes de compra pertenecientes al usuario
                    autenticado.

                    La identidad del usuario se obtiene directamente desde el JWT
                    mediante @CurrentUserId. El cliente no puede especificar
                    manualmente el usuario cuyas órdenes desea consultar.

                    Los resultados se entregan de forma paginada.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Órdenes obtenidas exitosamente."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "El usuario no está autenticado o el token JWT no es válido.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Ocurrió un error interno al consultar las órdenes.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<Page<OrderResponse>>> getMyOrders(
            @ParameterObject
            Pageable pageable,

            @Parameter(
                    description = "UUID del usuario autenticado obtenido desde el JWT.",
                    hidden = true
            )
            UUID userId
    );
}
