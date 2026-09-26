package cl.eventpass.ms_auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    public void blacklistToken(String tokenId, long remainingMillis) {
        if (remainingMillis > 0) {
            String key = BLACKLIST_PREFIX + tokenId;
            redisTemplate.opsForValue().set(
                    key,
                    "revoked",
                    Duration.ofMillis(remainingMillis)
            );
        }
    }

    public boolean isBlacklisted(String tokenId) {
        String key = BLACKLIST_PREFIX + tokenId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
