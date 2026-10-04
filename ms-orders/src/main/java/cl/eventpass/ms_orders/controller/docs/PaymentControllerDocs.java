package cl.eventpass.ms_orders.controller.docs;

import cl.eventpass.ms_orders.dto.request.PaymentRequest;
import cl.eventpass.ms_orders.dto.response.OrderResponse;
import cl.eventpass.ms_orders.dto.response.StandardErrorResponse;
import cl.eventpass.ms_orders.dto.response.StandardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@Tag(
        name = "Payments",
        description = "Operaciones relacionadas con el procesamiento de pagos de órdenes."
)
@SecurityRequirement(name = "bearerAuth")
public interface PaymentControllerDocs {

    @Operation(
            summary = "Procesar pago de una orden",
            description = """
                    Procesa el pago de una orden perteneciente al usuario autenticado.

                    La identidad del usuario se obtiene mediante @CurrentUserId
                    a partir del JWT. El cliente no puede especificar manualmente
                    el usuario propietario de la orden.

                    Solo pueden procesarse pagos de órdenes que se encuentren
                    en estado PENDING y cuya fecha de expiración aún no haya sido
                    alcanzada.

                    Si el proveedor de pago aprueba la transacción, la orden
                    pasa a estado PAID y el estado de pago pasa a APPROVED.

                    Si el proveedor rechaza la transacción, la orden pasa a
                    estado CANCELLED, el estado de pago pasa a REJECTED y se
                    libera el aforo previamente reservado en ms-events.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Pago procesado correctamente."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "La solicitud contiene datos inválidos.",
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
                    description = "No se encontró la orden solicitada o no pertenece al usuario autenticado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = """
                            La orden no puede ser procesada porque no se encuentra
                            en estado PENDING, ya tiene un resultado de pago o ha
                            expirado.
                            """,
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Ocurrió un error interno al procesar el pago.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<OrderResponse>> processPayment(

            @Parameter(
                    name = "id",
                    description = "Identificador único de la orden.",
                    required = true,
                    in = ParameterIn.PATH
            )
            @PathVariable UUID id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Datos necesarios para procesar el pago.",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = PaymentRequest.class
                            )
                    )
            )
            @Valid @RequestBody PaymentRequest request,

            @Parameter(
                    description = "UUID del usuario autenticado obtenido desde el JWT.",
                    hidden = true
            )
            UUID userId
    );
}
