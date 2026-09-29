package cl.eventpass.ms_auth.controller;

import cl.eventpass.ms_auth.controller.docs.AdminControllerDocs;
import cl.eventpass.ms_auth.dto.request.CreateUserRequest;
import cl.eventpass.ms_auth.dto.request.UpdateUserRequest;
import cl.eventpass.ms_auth.dto.request.UpdateUserStatusRequest;
import cl.eventpass.ms_auth.dto.response.StandardResponse;
import cl.eventpass.ms_auth.dto.response.UserResponse;
import cl.eventpass.ms_auth.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController implements AdminControllerDocs {

    private final AdminService adminService;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<List<UserResponse>>> getAllUsers() {
        List<UserResponse> response = adminService.getAllUsers();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(
                        StandardResponse.ok(
                                "Usuarios obtenidos con éxito.",
                                response
                        )
                );
    }

    @Override
    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<UserResponse>> getUserById(@PathVariable UUID id) {
        UserResponse response = adminService.getUserById(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(StandardResponse.ok(
                        "Usuario obtenido exitosamente.",
                        response
                ));
    }

    @Override
    @PatchMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<UserResponse>> updateUser(@PathVariable UUID id, @RequestBody UpdateUserRequest request) {
        UserResponse response = adminService.updateUser(id, request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(
                        StandardResponse.ok(
                                "Usuario actualizado con exito.",
                                response
                        )
                );
    }

    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<UserResponse>> updateUserStatus(@PathVariable UUID id, @RequestBody UpdateUserStatusRequest request) {
        UserResponse response = adminService.updateUserStatus(id, request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(
                        StandardResponse.ok(
                                "Estado del usuario actualizado con exito.",
                                response
                        )
                );
    }

    @Override
    @PostMapping("/users/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<UserResponse>> createAdmin(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = adminService.createAdmin(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        StandardResponse.created(
                                "Administrador creado exitosamente.",
                                response
                        )
                );
    }

    @Override
    @PostMapping("/users/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<UserResponse>> createStaff(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = adminService.createStaff(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        StandardResponse.created(
                                "Staff creado exitosamente.",
                                response
                        )
                );
    }

    @Override
    @PostMapping("/users/organizer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<UserResponse>> createOrganizer(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = adminService.createOrganizer(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        StandardResponse.created(
                                "Organizador creado exitosamente.",
                                response
                        )
                );
    }

    @Override
    @PostMapping("/users/support")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StandardResponse<UserResponse>> createSupport(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = adminService.createSupport(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        StandardResponse.created(
                                "Usuario de soporte creado exitosamente.",
                                response
                        )
                );
    }
}
