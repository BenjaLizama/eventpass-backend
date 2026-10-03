package cl.eventpass.ms_auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.jsonwebtoken.ExpiredJwtException;
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final String SECRET_KEY =
            "c3VwZXItc2VjcmV0LWtleS1mb3ItZXZlbnRwYXNzLXRlc3RzLTIwMjYtZm9yLUhTMjU2";

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private SessionService sessionService;

    private JwtService jwtService;

    private UserDetails userDetails;
    private UUID userId;
    private String sessionId;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                tokenBlacklistService,
                sessionService
        );

        ReflectionTestUtils.setField(
                jwtService,
                "secretKey",
                SECRET_KEY
        );

        ReflectionTestUtils.setField(
                jwtService,
                "jwtExpiration",
                60_000L
        );

        ReflectionTestUtils.setField(
                jwtService,
                "refreshExpiration",
                120_000L
        );

        userDetails = new User(
                "usuario@test.com",
                "password-encriptada",
                List.of(
                        new SimpleGrantedAuthority("ROLE_CLIENT")
                )
        );

        userId = UUID.randomUUID();
        sessionId = "session-test-123";
    }
    @Test
    void generateToken_ShouldIncludeUserDataAndAuthorities() {
        // Arrange / Act
        String token = jwtService.generateToken(
                userDetails,
                userId,
                sessionId
        );

        // Assert
        assertNotNull(token);
        assertEquals("usuario@test.com", jwtService.extractUsername(token));
        assertEquals(userId.toString(), jwtService.extractUserId(token));
        assertEquals(sessionId, jwtService.extractSessionId(token));
        assertNotNull(jwtService.extractJti(token));
        assertNotNull(jwtService.extractExpiration(token));

        List<?> authorities = jwtService.extractClaim(
                token,
                claims -> claims.get("authorities", List.class)
        );

        assertNotNull(authorities);
        assertEquals(1, authorities.size());
        assertEquals("ROLE_CLIENT", authorities.getFirst());
    }
    @Test
    void generateRefreshToken_ShouldIncludeUserAndSessionData() {
        // Arrange / Act
        String refreshToken = jwtService.generateRefreshToken(
                userDetails,
                userId,
                sessionId
        );

        // Assert
        assertNotNull(refreshToken);
        assertEquals("usuario@test.com", jwtService.extractUsername(refreshToken));
        assertEquals(userId.toString(), jwtService.extractUserId(refreshToken));
        assertEquals(sessionId, jwtService.extractSessionId(refreshToken));
        assertNotNull(jwtService.extractJti(refreshToken));
        assertNotNull(jwtService.extractExpiration(refreshToken));

        List<?> authorities = jwtService.extractClaim(
                refreshToken,
                claims -> claims.get("authorities", List.class)
        );

        assertNull(authorities);
    }
    @Test
    void isTokenValid_WhenTokenIsActiveAndNotBlacklisted_ReturnsTrue() {
        // Arrange
        String token = jwtService.generateToken(
                userDetails,
                userId,
                sessionId
        );

        String jti = jwtService.extractJti(token);

        when(tokenBlacklistService.isBlacklisted(jti))
                .thenReturn(false);

        when(sessionService.isSessionActive(sessionId))
                .thenReturn(true);

        // Act
        boolean result = jwtService.isTokenValid(token, userDetails);

        // Assert
        assertTrue(result);

        verify(tokenBlacklistService)
                .isBlacklisted(jti);

        verify(sessionService)
                .isSessionActive(sessionId);
    }
    @Test
    void isTokenValid_WhenTokenIsBlacklisted_ReturnsFalse() {
        // Arrange
        String token = jwtService.generateToken(
                userDetails,
                userId,
                sessionId
        );

        String jti = jwtService.extractJti(token);

        when(tokenBlacklistService.isBlacklisted(jti))
                .thenReturn(true);

        // Act
        boolean result = jwtService.isTokenValid(token, userDetails);

        // Assert
        assertFalse(result);

        verify(tokenBlacklistService)
                .isBlacklisted(jti);

        verify(sessionService, never())
                .isSessionActive(anyString());
    }
    @Test
    void isTokenValid_WhenSessionIsInactive_ReturnsFalse() {
        // Arrange
        String token = jwtService.generateToken(
                userDetails,
                userId,
                sessionId
        );

        String jti = jwtService.extractJti(token);

        when(tokenBlacklistService.isBlacklisted(jti))
                .thenReturn(false);

        when(sessionService.isSessionActive(sessionId))
                .thenReturn(false);

        // Act
        boolean result = jwtService.isTokenValid(token, userDetails);

        // Assert
        assertFalse(result);

        verify(tokenBlacklistService)
                .isBlacklisted(jti);

        verify(sessionService)
                .isSessionActive(sessionId);
    }
    @Test
    void isTokenExpired_WhenExpirationIsInPast_ThrowsExpiredJwtException() {
        // Arrange
        ReflectionTestUtils.setField(
                jwtService,
                "jwtExpiration",
                -1L
        );

        String expiredToken = jwtService.generateToken(
                userDetails,
                userId,
                sessionId
        );

        // Act and Assert
        assertThrows(
                ExpiredJwtException.class,
                () -> jwtService.isTokenExpired(expiredToken)
        );
    }
}