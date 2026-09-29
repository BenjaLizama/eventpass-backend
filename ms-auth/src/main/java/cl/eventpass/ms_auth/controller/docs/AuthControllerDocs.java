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
            description = "Obtiene la información del perfil del usuario que se encuentra "
                    + "actualmente autenticado a partir del Token JWT enviado en la cabecera de autorización."
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
                    description = "El usuario asociado al token no existe o se encuentra inactivo.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<UserResponse>> me(
            @Parameter(hidden = true)Authentication authentication
    );

    @Operation(
            summary = "Registrar nuevo cliente",
            description = "Crea una nueva cuenta con el rol CUSTOMER asignado por defecto " +
                    "y devuelve la pareja inicial de tokens JWT."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Usuario registrado y autenticado exitosamente."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Error de validación en la estructura o formato de las credenciales.",
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
            description = "Autentica las credenciales enviadas y genera tokens Access y Refresh activos."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Autenticación exitosa."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Error de validación en el cuerpo de la petición.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Credenciales inválidas (email o contraseña incorrectos).",
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
            description = "Genera un nuevo Access Token a partir de un Refresh Token " +
                    "válido que no haya expirado ni sido revocado."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Token renovado exitosamente."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Petición malformada o campo de token faltante.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Refresh token inválido, expirado o malformado.",
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
            description = "Revoca la sesión asociada al Access Token actual. "
                    + "La sesión se elimina de Redis, invalidando tanto el Access Token "
                    + "como el Refresh Token asociados a ella."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Sesión cerrada correctamente. Los tokens asociados a la sesión han sido revocados."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Cabecera de autorización ausente, formato de token "
                            + "Bearer incorrecto o token ya inválido.",
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
