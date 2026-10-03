package cl.eventpass.ms_auth.controller;

import cl.eventpass.ms_auth.dto.request.ServiceTokenRequest;
import cl.eventpass.ms_auth.dto.response.ServiceTokenResponse;
import cl.eventpass.ms_auth.dto.response.StandardResponse;
import cl.eventpass.ms_auth.service.ServiceAuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class ServiceAuthenticationController {

    private final ServiceAuthenticationService serviceAuthenticationService;

    @PostMapping("/service-token")
    public ResponseEntity<StandardResponse<ServiceTokenResponse>> generateToken(
            @Valid @RequestBody ServiceTokenRequest request
    ) {

        ServiceTokenResponse response =
                serviceAuthenticationService.generateToken(request);

        return ResponseEntity.ok(
                StandardResponse.ok(
                        "Token de servicio generado correctamente.",
                        response
                )
        );
    }
}
