package cl.eventpass.ms_auth.service;

import cl.eventpass.ms_auth.config.MsOrdersClientProperties;
import cl.eventpass.ms_auth.dto.request.ServiceTokenRequest;
import cl.eventpass.ms_auth.dto.response.ServiceTokenResponse;
import cl.eventpass.ms_auth.enums.Role;
import cl.eventpass.ms_auth.exception.InvalidServiceCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServiceAuthenticationService {

    private final JwtService jwtService;
    private final MsOrdersClientProperties msOrdersClientProperties;

    public ServiceTokenResponse generateToken(
            ServiceTokenRequest request
    ) {

        validateClientCredentials(request);

        String accessToken =
                jwtService.generateServiceToken(
                        "ms-orders",
                        Role.INTERNAL_SERVICE
                );

        return new ServiceTokenResponse(
                accessToken,
                "Bearer",
                jwtService.getServiceTokenExpirationSeconds()
        );
    }

    private void validateClientCredentials(
            ServiceTokenRequest request
    ) {

        boolean validClientId =
                msOrdersClientProperties
                        .getClientId()
                        .equals(request.clientId());

        boolean validClientSecret =
                msOrdersClientProperties
                        .getClientSecret()
                        .equals(request.clientSecret());

        if (!validClientId || !validClientSecret) {
            throw new InvalidServiceCredentialsException(
                    "Credenciales del servicio inválidas."
            );
        }
    }
}
