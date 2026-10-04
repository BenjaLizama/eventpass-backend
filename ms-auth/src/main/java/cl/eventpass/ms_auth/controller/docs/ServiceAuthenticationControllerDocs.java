package cl.eventpass.ms_auth.controller.docs;

import cl.eventpass.ms_auth.dto.request.ServiceTokenRequest;
import cl.eventpass.ms_auth.dto.response.ServiceTokenResponse;
import cl.eventpass.ms_auth.dto.response.StandardErrorResponse;
import cl.eventpass.ms_auth.dto.response.StandardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(
        name = "Service Authentication",
        description = "Endpoints para la autenticación interna entre microservicios."
)
public interface ServiceAuthenticationControllerDocs {

    @Operation(
            summary = "Generar token de servicio",
            description = """
                    Genera un JWT de servicio para la comunicación segura
                    entre microservicios.

                    El cliente debe proporcionar sus credenciales internas
                    (client ID y client secret). Si las credenciales son válidas,
                    ms-auth genera un token JWT con permisos de servicio interno.

                    Este endpoint es utilizado por microservicios autorizados,
                    como ms-orders, para autenticarse frente a otros servicios
                    de EventPass.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Token de servicio generado correctamente."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Las credenciales enviadas no cumplen las validaciones requeridas.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Las credenciales del servicio son inválidas.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Ocurrió un error interno al generar el token de servicio.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<ServiceTokenResponse>> generateToken(
            @Valid
            @RequestBody
            ServiceTokenRequest request
    );
}
