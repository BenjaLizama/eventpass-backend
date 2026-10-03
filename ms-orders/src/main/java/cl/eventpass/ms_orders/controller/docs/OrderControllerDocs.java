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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
            description = "Crea una nueva orden de compra para una categoría de ticket. "
                    + "El usuario autenticado se obtiene desde el token JWT. "
                    + "El precio de la categoría se obtiene desde ms-events y el aforo "
                    + "se reserva antes de crear la orden."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Orden creada exitosamente."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Los datos de la solicitud son inválidos o la cantidad "
                            + "solicitada supera el máximo permitido por usuario.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "El usuario no está autenticado.",
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
                    description = "Ocurrió un error al procesar la creación de la orden.",
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

            UUID userId
    );
}
