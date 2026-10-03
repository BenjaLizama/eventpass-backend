package cl.eventpass.ms_auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        tokenBlacklistService = new TokenBlacklistService(redisTemplate);
    }

    @Test
    void blacklistToken_WhenDurationIsPositive_StoresTokenWithCorrectTtl() {
        // Arrange
        String tokenId = "token-id-123";
        long remainingMillis = 60_000L;

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        // Act
        tokenBlacklistService.blacklistToken(tokenId, remainingMillis);

        // Assert
        verify(valueOperations).set(
                "jwt:blacklist:" + tokenId,
                "revoked",
                Duration.ofMillis(remainingMillis)
        );
    }

    @Test
    void blacklistToken_WhenDurationIsZero_DoesNotAccessRedis() {
        // Act
        tokenBlacklistService.blacklistToken("token-id-123", 0L);

        // Assert
        verifyNoInteractions(redisTemplate, valueOperations);
    }

    @Test
    void blacklistToken_WhenDurationIsNegative_DoesNotAccessRedis() {
        // Act
        tokenBlacklistService.blacklistToken("token-id-123", -1L);

        // Assert
        verifyNoInteractions(redisTemplate, valueOperations);
    }

    @Test
    void isBlacklisted_WhenKeyExists_ReturnsTrue() {
        // Arrange
        String tokenId = "token-id-123";

        when(redisTemplate.hasKey("jwt:blacklist:" + tokenId))
                .thenReturn(true);

        // Act
        boolean result = tokenBlacklistService.isBlacklisted(tokenId);

        // Assert
        assertTrue(result);

        verify(redisTemplate)
                .hasKey("jwt:blacklist:" + tokenId);
    }

    @Test
    void isBlacklisted_WhenKeyDoesNotExist_ReturnsFalse() {
        // Arrange
        String tokenId = "token-id-123";

        when(redisTemplate.hasKey("jwt:blacklist:" + tokenId))
                .thenReturn(false);

        // Act
        boolean result = tokenBlacklistService.isBlacklisted(tokenId);

        // Assert
        assertFalse(result);

        verify(redisTemplate)
                .hasKey("jwt:blacklist:" + tokenId);
    }

    @Test
    void isBlacklisted_WhenRedisReturnsNull_ReturnsFalse() {
        // Arrange
        String tokenId = "token-id-123";

        when(redisTemplate.hasKey("jwt:blacklist:" + tokenId))
                .thenReturn(null);

        // Act
        boolean result = tokenBlacklistService.isBlacklisted(tokenId);

        // Assert
        assertFalse(result);
    }
}