package cl.eventpass.ms_auth.controller;

import cl.eventpass.ms_auth.controller.docs.AuthControllerDocs;
import cl.eventpass.ms_auth.dto.request.LoginRequest;
import cl.eventpass.ms_auth.dto.request.RefreshTokenRequest;
import cl.eventpass.ms_auth.dto.request.RegisterRequest;
import cl.eventpass.ms_auth.dto.response.AuthResponse;
import cl.eventpass.ms_auth.dto.response.StandardResponse;
import cl.eventpass.ms_auth.dto.response.UserResponse;
import cl.eventpass.ms_auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final AuthService authService;

    @Override
    @GetMapping("/me")
    public ResponseEntity<StandardResponse<UserResponse>> me(Authentication authentication) {
        UserResponse response = authService.getCurrentUser(authentication.getName());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(StandardResponse.ok("Usuario obtenido exitosamente.", response));
    }

    @Override
    @PostMapping("/register")
    public ResponseEntity<StandardResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(StandardResponse.created("Usuario registrado exitosamente.", response));
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<StandardResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(
                StandardResponse.ok(
                        "Autenticación exitosa.",
                        response
                )
        );
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<StandardResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(
                StandardResponse.ok(
                        "Token renovado exitosamente.",
                        response
                )
        );
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<StandardResponse<Void>> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        authService.logout(authHeader);

        return ResponseEntity.ok(
                StandardResponse.ok(
                        "Sesión cerrada exitosamente.",
                        null
                )
        );
    }
}
