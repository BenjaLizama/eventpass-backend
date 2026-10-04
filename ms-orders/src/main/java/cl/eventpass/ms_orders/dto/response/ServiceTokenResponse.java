package cl.eventpass.ms_orders.dto.response;

public record ServiceTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
