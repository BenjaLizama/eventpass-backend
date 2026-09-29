package cl.eventpass.ms_auth.controller.docs;

import cl.eventpass.ms_auth.dto.request.LoginRequest;
import cl.eventpass.ms_auth.dto.request.RefreshTokenRequest;
import cl.eventpass.ms_auth.dto.request.RegisterRequest;
import cl.eventpass.ms_auth.dto.response.AuthResponse;
import cl.eventpass.ms_auth.dto.response.StandardErrorResponse;
import cl.eventpass.ms_auth.dto.response.StandardResponse;
import cl.eventpass.ms_auth.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(
        name = "Authentication",
        description = "Endpoints para registro, inicio de sesión, renovación de tokens y cierre de sesión."
)
public interface AuthControllerDocs {

    @Operation(
            summary = "Obtener perfil del usuario autenticado",
            description = "Obtiene la información del perfil del usuario actualmente autenticado "
                    + "a partir del usuario asociado al Token JWT enviado en la cabecera de autorización."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Perfil de usuario obtenido exitosamente."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No existe una autenticación válida, el token ha expirado o no fue provisto.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No se encontró el usuario asociado a la autenticación actual.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<UserResponse>> me(
            @Parameter(hidden = true)
            Authentication authentication
    );


    @Operation(
            summary = "Registrar nuevo cliente",
            description = "Crea una nueva cuenta con el rol CUSTOMER asignado por defecto "
                    + "y devuelve la pareja inicial de tokens JWT."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Usuario registrado y autenticado exitosamente."
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
                    responseCode = "409",
                    description = "El correo electrónico ingresado ya se encuentra registrado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<AuthResponse>> register(
            RegisterRequest request
    );


    @Operation(
            summary = "Iniciar sesión",
            description = "Autentica las credenciales enviadas y genera un Access Token "
                    + "y un Refresh Token asociados a una nueva sesión."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Autenticación exitosa."
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
                    description = "Las credenciales son inválidas o la cuenta no se encuentra habilitada para autenticarse.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<AuthResponse>> login(
            LoginRequest request
    );


    @Operation(
            summary = "Renovar Access Token",
            description = "Genera un nuevo Access Token utilizando un Refresh Token válido. "
                    + "El Refresh Token debe ser válido, no estar expirado y pertenecer a una sesión "
                    + "que continúe activa."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Access Token renovado exitosamente."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "La petición es malformada o el Refresh Token no fue proporcionado correctamente.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "El Refresh Token es inválido, está expirado, está malformado "
                            + "o la sesión asociada fue cerrada o revocada.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<AuthResponse>> refreshToken(
            RefreshTokenRequest request
    );


    @Operation(
            summary = "Cerrar sesión",
            description = "Cierra la sesión asociada al Access Token actual. "
                    + "La sesión queda revocada y el token actual es agregado a la lista de tokens "
                    + "revocados mientras permanezca vigente."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Sesión cerrada correctamente."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "La cabecera de autorización está ausente, tiene un formato incorrecto, "
                            + "el token es inválido o la sesión ya fue cerrada o revocada.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<Void>> logout(
            @Parameter(hidden = true)
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    );

}
