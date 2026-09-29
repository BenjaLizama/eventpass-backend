package cl.eventpass.ms_auth.controller.docs;

import cl.eventpass.ms_auth.dto.request.CreateUserRequest;
import cl.eventpass.ms_auth.dto.response.StandardErrorResponse;
import cl.eventpass.ms_auth.dto.response.StandardResponse;
import cl.eventpass.ms_auth.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(
        name = "Administration",
        description = "Endpoints administrativos para la gestión de usuarios internos del sistema."
)
@SecurityRequirement(name = "bearerAuth")
public interface AdminControllerDocs {

    @Operation(
            summary = "Crear administrador",
            description = "Crea una nueva cuenta con el rol ADMIN. "
                    + "Este endpoint solamente puede ser utilizado por un usuario que posea el rol ADMIN."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Administrador creado exitosamente."
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
                    description = "No existe una autenticación válida.",
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
                    responseCode = "409",
                    description = "El correo electrónico ya se encuentra registrado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<UserResponse>> createAdmin(
            CreateUserRequest request
    );


    @Operation(
            summary = "Crear usuario Staff",
            description = "Crea una nueva cuenta con el rol STAFF. "
                    + "Este rol está destinado al personal encargado de validar códigos de acceso a los eventos."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Usuario Staff creado exitosamente."
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
                    description = "No existe una autenticación válida.",
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
                    responseCode = "409",
                    description = "El correo electrónico ya se encuentra registrado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<UserResponse>> createStaff(
            CreateUserRequest request
    );


    @Operation(
            summary = "Crear organizador",
            description = "Crea una nueva cuenta con el rol ORGANIZER. "
                    + "Este rol permite gestionar eventos y consultar información relacionada con ellos."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Organizador creado exitosamente."
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
                    description = "No existe una autenticación válida.",
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
                    responseCode = "409",
                    description = "El correo electrónico ya se encuentra registrado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<UserResponse>> createOrganizer(
            CreateUserRequest request
    );


    @Operation(
            summary = "Crear usuario de soporte",
            description = "Crea una nueva cuenta con el rol SUPPORT. "
                    + "Este rol está destinado a operaciones de soporte sobre usuarios, eventos y tickets."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Usuario de soporte creado exitosamente."
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
                    description = "No existe una autenticación válida.",
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
                    responseCode = "409",
                    description = "El correo electrónico ya se encuentra registrado.",
                    content = @Content(
                            schema = @Schema(
                                    implementation = StandardErrorResponse.class
                            )
                    )
            )
    })
    ResponseEntity<StandardResponse<UserResponse>> createSupport(
            CreateUserRequest request
    );

    @Operation(
            summary = "Obtener todos los usuarios",
            description = "Obtiene el listado de todos los usuarios registrados en el sistema. "
                    + "Este endpoint solamente puede ser utilizado por un usuario que posea el rol ADMIN."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usuarios obtenidos exitosamente."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No existe una autenticación válida.",
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
            )
    })
    ResponseEntity<StandardResponse<List<UserResponse>>> getAllUsers();
}
