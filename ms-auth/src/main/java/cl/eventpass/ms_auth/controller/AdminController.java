package cl.eventpass.ms_auth.controller;

import cl.eventpass.ms_auth.controller.docs.AdminControllerDocs;
import cl.eventpass.ms_auth.dto.request.CreateUserRequest;
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
                                "Administrador creado exitosamente.",
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
                                "Administrador creado exitosamente.",
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
                                "Administrador creado exitosamente.",
                                response
                        )
                );
    }
}
