package cl.eventpass.ms_orders.dto.request;

public record ServiceTokenRequest(
        String clientId,
        String clientSecret
) {
}
