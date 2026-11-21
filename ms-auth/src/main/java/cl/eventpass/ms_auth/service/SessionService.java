package cl.eventpass.ms_auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String SESSION_PREFIX = "jwt:session:";
    private static final String USER_SESSIONS_PREFIX = "jwt:user-sessions:";

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
        redisTemplate.opsForSet().add(USER_SESSIONS_PREFIX + username, sessionId);
        redisTemplate.expire(USER_SESSIONS_PREFIX + username, Duration.ofMillis(refreshExpiration));
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

        String username = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        redisTemplate.delete(SESSION_PREFIX + sessionId);
        if (username != null) redisTemplate.opsForSet().remove(USER_SESSIONS_PREFIX + username, sessionId);
    }

    public void revokeUserSessions(String username) {
        // Revoke only this user's indexed sessions; never scan all users' keys.
        Set<String> sessions = redisTemplate.opsForSet().members(USER_SESSIONS_PREFIX + username);
        if (sessions != null && !sessions.isEmpty())
            redisTemplate.delete(sessions.stream().map(id -> SESSION_PREFIX + id).toList());
        redisTemplate.delete(USER_SESSIONS_PREFIX + username);
    }
}
