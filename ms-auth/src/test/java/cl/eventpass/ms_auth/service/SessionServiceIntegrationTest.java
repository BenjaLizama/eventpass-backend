package cl.eventpass.ms_auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringJUnitConfig(
        SessionServiceIntegrationTest.RedisTestConfig.class
)
@TestPropertySource(properties = {
        "application.security.jwt.refresh-token.expiration=3000"
})
class SessionServiceIntegrationTest {

    private static final long SESSION_TTL_MILLIS = 3_000L;

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @Autowired
    private SessionService sessionService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void createSession_StoresUsernameAndTtlInRedis() {
        // Arrange
        String sessionId = UUID.randomUUID().toString();
        String username = "usuario@test.com";
        String key = "jwt:session:" + sessionId;

        // Act
        sessionService.createSession(sessionId, username);

        // Assert
        assertEquals(
                username,
                redisTemplate.opsForValue().get(key)
        );

        assertTrue(sessionService.isSessionActive(sessionId));

        Long ttl = redisTemplate.getExpire(
                key,
                TimeUnit.MILLISECONDS
        );

        assertNotNull(ttl);
        assertTrue(ttl > 0);
        assertTrue(ttl <= SESSION_TTL_MILLIS);
    }

    @Test
    void isSessionActive_WhenSessionDoesNotExist_ReturnsFalse() {
        // Arrange
        String sessionId = UUID.randomUUID().toString();

        // Act
        boolean result = sessionService.isSessionActive(sessionId);

        // Assert
        assertFalse(result);
    }

    @Test
    void revokeSession_RemovesSessionFromRedis() {
        // Arrange
        String sessionId = UUID.randomUUID().toString();
        String key = "jwt:session:" + sessionId;

        sessionService.createSession(
                sessionId,
                "usuario@test.com"
        );

        assertTrue(sessionService.isSessionActive(sessionId));

        // Act
        sessionService.revokeSession(sessionId);

        // Assert
        assertFalse(sessionService.isSessionActive(sessionId));

        assertNull(
                redisTemplate.opsForValue().get(key)
        );
    }

    @Test
    void isSessionActive_WhenTtlExpires_ReturnsFalse() {
        // Arrange
        String sessionId = UUID.randomUUID().toString();
        String key = "jwt:session:" + sessionId;

        // Act
        sessionService.createSession(
                sessionId,
                "usuario@test.com"
        );

        // Assert
        assertTrue(sessionService.isSessionActive(sessionId));

        await()
                .atMost(Duration.ofSeconds(8))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    assertFalse(
                            sessionService.isSessionActive(sessionId)
                    );

                    assertNull(
                            redisTemplate.opsForValue().get(key)
                    );
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisTestConfig {

        @Bean
        LettuceConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory(
                    redis.getHost(),
                    redis.getMappedPort(6379)
            );
        }

        @Bean
        StringRedisTemplate redisTemplate(
                LettuceConnectionFactory connectionFactory
        ) {
            return new StringRedisTemplate(connectionFactory);
        }

        @Bean
        SessionService sessionService(
                StringRedisTemplate redisTemplate
        ) {
            return new SessionService(redisTemplate);
        }
    }
}