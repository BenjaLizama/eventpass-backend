package cl.eventpass.ms_orders.client;

import cl.eventpass.ms_orders.config.ServiceAuthProperties;
import cl.eventpass.ms_orders.dto.request.ServiceTokenRequest;
import cl.eventpass.ms_orders.dto.response.ServiceTokenResponse;
import cl.eventpass.ms_orders.dto.response.StandardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class AuthClient {

    private final RestClient authRestClient;
    private final ServiceAuthProperties properties;

    public ServiceTokenResponse requestServiceToken() {

        StandardResponse<ServiceTokenResponse> response =
                authRestClient
                        .post()
                        .uri("/api/v1/auth/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                new ServiceTokenRequest(
                                        properties.getClientId(),
                                        properties.getClientSecret()
                                )
                        )
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<
                                        StandardResponse<ServiceTokenResponse>
                                        >() {
                                }
                        );

        if (response == null || response.data() == null) {
            throw new IllegalStateException(
                    "ms-auth no devolvió un token de servicio válido."
            );
        }

        return response.data();
    }
}
