package cl.eventpass.ms_orders.security;

import cl.eventpass.ms_orders.client.AuthClient;
import cl.eventpass.ms_orders.dto.response.ServiceTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServiceTokenProvider {

    private static final long RENEWAL_MARGIN_SECONDS = 60;

    private final AuthClient authClient;

    private volatile String accessToken;
    private volatile long expiresAt;

    public synchronized String getToken() {

        if (isTokenValid()) {
            return accessToken;
        }

        ServiceTokenResponse response =
                authClient.requestServiceToken();

        this.accessToken = response.accessToken();

        this.expiresAt =
                System.currentTimeMillis()
                        + (response.expiresIn() * 1000L);

        return accessToken;
    }

    private boolean isTokenValid() {

        if (accessToken == null) {
            return false;
        }

        long renewalMarginMillis =
                RENEWAL_MARGIN_SECONDS * 1000L;

        return System.currentTimeMillis()
                < expiresAt - renewalMarginMillis;
    }
}
