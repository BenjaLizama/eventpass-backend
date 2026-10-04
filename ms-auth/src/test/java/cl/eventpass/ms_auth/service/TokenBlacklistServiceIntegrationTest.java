package cl.eventpass.ms_auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
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
        TokenBlacklistServiceIntegrationTest.RedisTestConfig.class
)
class TokenBlacklistServiceIntegrationTest {

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void blacklistToken_StoresRevocationWithTtlInRedis() {
        // Arrange
        String tokenId = UUID.randomUUID().toString();
        String key = "jwt:blacklist:" + tokenId;
        long remainingMillis = 30_000L;

        // Act
        tokenBlacklistService.blacklistToken(tokenId, remainingMillis);

        // Assert
        assertEquals(
                "revoked",
                redisTemplate.opsForValue().get(key)
        );

        assertTrue(tokenBlacklistService.isBlacklisted(tokenId));

        Long ttl = redisTemplate.getExpire(
                key,
                TimeUnit.MILLISECONDS
        );

        assertNotNull(ttl);
        assertTrue(ttl > 0);
        assertTrue(ttl <= remainingMillis);
    }

    @Test
    void isBlacklisted_WhenTokenWasNotStored_ReturnsFalse() {
        // Arrange
        String tokenId = UUID.randomUUID().toString();

        // Act
        boolean result = tokenBlacklistService.isBlacklisted(tokenId);

        // Assert
        assertFalse(result);
    }

    @Test
    void blacklistToken_WhenTtlExpires_RemovesRevocationFromRedis() {
        // Arrange
        String tokenId = UUID.randomUUID().toString();
        String key = "jwt:blacklist:" + tokenId;

        // Act
        tokenBlacklistService.blacklistToken(tokenId, 1_000L);

        // Assert
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    assertFalse(
                            tokenBlacklistService.isBlacklisted(tokenId)
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
        TokenBlacklistService tokenBlacklistService(
                StringRedisTemplate redisTemplate
        ) {
            return new TokenBlacklistService(redisTemplate);
        }
    }
}