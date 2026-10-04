package cl.eventpass.ms_auth.dto.response;

public record ServiceTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
