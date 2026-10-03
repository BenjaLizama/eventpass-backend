package cl.eventpass.ms_auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final long REFRESH_EXPIRATION = 120_000L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(redisTemplate);

        ReflectionTestUtils.setField(
                sessionService,
                "refreshExpiration",
                REFRESH_EXPIRATION
        );
    }

    @Test
    void createSession_StoresUsernameWithCorrectKeyAndTtl() {
        // Arrange
        String sessionId = "session-id-123";
        String username = "usuario@test.com";

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        // Act
        sessionService.createSession(sessionId, username);

        // Assert
        verify(valueOperations).set(
                "jwt:session:" + sessionId,
                username,
                Duration.ofMillis(REFRESH_EXPIRATION)
        );
    }

    @Test
    void isSessionActive_WhenKeyExists_ReturnsTrue() {
        // Arrange
        String sessionId = "session-id-123";

        when(redisTemplate.hasKey("jwt:session:" + sessionId))
                .thenReturn(true);

        // Act
        boolean result = sessionService.isSessionActive(sessionId);

        // Assert
        assertTrue(result);

        verify(redisTemplate)
                .hasKey("jwt:session:" + sessionId);
    }

    @Test
    void isSessionActive_WhenKeyDoesNotExist_ReturnsFalse() {
        // Arrange
        String sessionId = "session-id-123";

        when(redisTemplate.hasKey("jwt:session:" + sessionId))
                .thenReturn(false);

        // Act
        boolean result = sessionService.isSessionActive(sessionId);

        // Assert
        assertFalse(result);
    }

    @Test
    void isSessionActive_WhenRedisReturnsNull_ReturnsFalse() {
        // Arrange
        String sessionId = "session-id-123";

        when(redisTemplate.hasKey("jwt:session:" + sessionId))
                .thenReturn(null);

        // Act
        boolean result = sessionService.isSessionActive(sessionId);

        // Assert
        assertFalse(result);
    }

    @Test
    void isSessionActive_WhenSessionIdIsNull_ReturnsFalseWithoutAccessingRedis() {
        // Act
        boolean result = sessionService.isSessionActive(null);

        // Assert
        assertFalse(result);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void isSessionActive_WhenSessionIdIsEmpty_ReturnsFalseWithoutAccessingRedis() {
        // Act
        boolean result = sessionService.isSessionActive("");

        // Assert
        assertFalse(result);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void isSessionActive_WhenSessionIdIsBlank_ReturnsFalseWithoutAccessingRedis() {
        // Act
        boolean result = sessionService.isSessionActive("   ");

        // Assert
        assertFalse(result);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void revokeSession_WhenSessionIdIsValid_DeletesCorrectKey() {
        // Arrange
        String sessionId = "session-id-123";

        // Act
        sessionService.revokeSession(sessionId);

        // Assert
        verify(redisTemplate)
                .delete("jwt:session:" + sessionId);
    }

    @Test
    void revokeSession_WhenSessionIdIsNull_DoesNotAccessRedis() {
        // Act
        sessionService.revokeSession(null);

        // Assert
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void revokeSession_WhenSessionIdIsEmpty_DoesNotAccessRedis() {
        // Act
        sessionService.revokeSession("");

        // Assert
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void revokeSession_WhenSessionIdIsBlank_DoesNotAccessRedis() {
        // Act
        sessionService.revokeSession("   ");

        // Assert
        verifyNoInteractions(redisTemplate);
    }
}