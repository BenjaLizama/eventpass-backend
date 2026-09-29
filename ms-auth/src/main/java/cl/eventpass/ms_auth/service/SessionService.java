package cl.eventpass.ms_auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String SESSION_PREFIX = "jwt:session:";

    private final StringRedisTemplate redisTemplate;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    public void createSession(
            String sessionId,
            String username
    ) {
        redisTemplate.opsForValue().set(
                SESSION_PREFIX + sessionId,
                username,
                Duration.ofMillis(refreshExpiration)
        );
    }

    public boolean isSessionActive(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }

        return Boolean.TRUE.equals(
                redisTemplate.hasKey(SESSION_PREFIX + sessionId)
        );
    }

    public void revokeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        redisTemplate.delete(
                SESSION_PREFIX + sessionId
        );
    }
}
