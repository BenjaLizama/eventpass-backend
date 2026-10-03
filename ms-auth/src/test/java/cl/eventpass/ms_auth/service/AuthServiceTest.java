package cl.eventpass.ms_auth.service;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import cl.eventpass.ms_auth.entity.CredentialEntity;
import cl.eventpass.ms_auth.mapper.AuthMapper;
import cl.eventpass.ms_auth.repository.CredentialRepository;
import cl.eventpass.ms_auth.dto.request.RegisterRequest;
import cl.eventpass.ms_auth.dto.response.AuthResponse;
import cl.eventpass.ms_auth.exception.EmailAlreadyExistsException;
import cl.eventpass.ms_auth.dto.request.LoginRequest;
import cl.eventpass.ms_auth.exception.ResourceNotFoundException;
import cl.eventpass.ms_auth.exception.InvalidTokenException;
import cl.eventpass.ms_auth.dto.request.RefreshTokenRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                authService,
                "jwtExpiration",
                86400000L
        );
    }

    @Test
    void getCurrentUser_WhenUserExists_ReturnsUserResponse() {
        String email = "usuario@test.com";
        CredentialEntity credential = mock(CredentialEntity.class);
        when(credentialRepository.findByEmailAndDeletedAtIsNull(email))
                .thenReturn(Optional.of(credential));

        var result = authService.getCurrentUser(email);

        assertNotNull(result);
        verify(credentialRepository)
                .findByEmailAndDeletedAtIsNull(email);
    }

    @Test
    void getCurrentUser_WhenUserDoesNotExist_ThrowsException() {
        String email = "inexistente@test.com";

        when(credentialRepository.findByEmailAndDeletedAtIsNull(email))
                .thenReturn(Optional.empty());

        assertThrows(
                RuntimeException.class,
                () -> authService.getCurrentUser(email)
        );

        verify(credentialRepository)
                .findByEmailAndDeletedAtIsNull(email);
    }

    /* Register */
    @Test
    void register_WhenEmailIsAvailable_CreatesUserAndReturnsAuthResponse() {
        // Arrange
        RegisterRequest request = mock(RegisterRequest.class);

        when(request.email()).thenReturn("nuevo@test.com");
        when(request.password()).thenReturn("Password123!");

        CredentialEntity credential = mock(CredentialEntity.class);
        UUID userId = UUID.randomUUID();

        when(credential.getId())
                .thenReturn(userId);

        when(credentialRepository.findByEmailAndDeletedAtIsNull("nuevo@test.com"))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode("Password123!"))
                .thenReturn("password-encriptada");

        when(authMapper.toEntity(request, "password-encriptada"))
                .thenReturn(credential);

        when(credentialRepository.save(credential))
                .thenReturn(credential);

        when(credential.getUsername())
                .thenReturn("nuevo@test.com");

        when(jwtService.generateToken(
                any(),
                any(UUID.class),
                anyString()
        )).thenReturn("access-token");

        when(jwtService.generateRefreshToken(
                any(),
                any(UUID.class),
                anyString()
        )).thenReturn("refresh-token");

        // Act
        AuthResponse response = authService.register(request);

        // Assert
        assertNotNull(response);
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(86400, response.expiresIn());

        verify(passwordEncoder)
                .encode("Password123!");

        verify(authMapper)
                .toEntity(request, "password-encriptada");

        verify(credentialRepository)
                .save(credential);

        verify(sessionService)
                .createSession(any(), eq("nuevo@test.com"));
    }
    @Test
    void register_WhenEmailAlreadyExists_ThrowsEmailAlreadyExistsException() {
        // Arrange
        RegisterRequest request = mock(RegisterRequest.class);

        when(request.email()).thenReturn("usuario@test.com");

        CredentialEntity existingCredential = mock(CredentialEntity.class);

        when(credentialRepository.findByEmailAndDeletedAtIsNull("usuario@test.com"))
                .thenReturn(Optional.of(existingCredential));

        // Act and Assert
        assertThrows(
                EmailAlreadyExistsException.class,
                () -> authService.register(request)
        );

        verify(credentialRepository)
                .findByEmailAndDeletedAtIsNull("usuario@test.com");

        verifyNoInteractions(passwordEncoder, authMapper);
    }
    @Test
    void login_WhenCredentialsAreValid_ReturnsAuthResponse() {
        // Arrange
        LoginRequest request = new LoginRequest(
                "usuario@test.com",
                "Password123!"
        );

        CredentialEntity credential = mock(CredentialEntity.class);
        UUID userId = UUID.randomUUID();

        when(credential.getId())
                .thenReturn(userId);

        when(credential.getUsername())
                .thenReturn("usuario@test.com");

        when(credentialRepository.findByEmailAndDeletedAtIsNull(
                "usuario@test.com"
        )).thenReturn(Optional.of(credential));

        when(jwtService.generateToken(
                any(),
                any(UUID.class),
                anyString()
        )).thenReturn("access-token");

        when(jwtService.generateRefreshToken(
                any(),
                any(UUID.class),
                anyString()
        )).thenReturn("refresh-token");

        // Act
        AuthResponse response = authService.login(request);

        // Assert
        assertNotNull(response);
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(86400, response.expiresIn());

        verify(authenticationManager).authenticate(
                argThat(authentication ->
                        authentication instanceof UsernamePasswordAuthenticationToken
                                && authentication.getPrincipal()
                                .equals("usuario@test.com")
                                && authentication.getCredentials()
                                .equals("Password123!")
                )
        );

        verify(credentialRepository)
                .findByEmailAndDeletedAtIsNull("usuario@test.com");

        verify(sessionService)
                .createSession(any(), eq("usuario@test.com"));
    }
    @Test
    void login_WhenUserDoesNotExist_ThrowsResourceNotFoundException() {
        // Arrange
        LoginRequest request = new LoginRequest(
                "inexistente@test.com",
                "Password123!"
        );

        when(credentialRepository.findByEmailAndDeletedAtIsNull(
                "inexistente@test.com"
        )).thenReturn(Optional.empty());

        // Act and Assert
        assertThrows(
                ResourceNotFoundException.class,
                () -> authService.login(request)
        );

        verify(authenticationManager).authenticate(
                any(UsernamePasswordAuthenticationToken.class)
        );

        verify(credentialRepository)
                .findByEmailAndDeletedAtIsNull("inexistente@test.com");

        verifyNoInteractions(jwtService, sessionService);
    }
    @Test
    void logout_WhenAuthorizationHeaderIsNull_ThrowsInvalidTokenException() {
        // Act and Assert
        assertThrows(
                InvalidTokenException.class,
                () -> authService.logout(null)
        );

        verifyNoInteractions(
                jwtService,
                sessionService,
                tokenBlacklistService
        );

    }
    @Test
    void logout_WhenAuthorizationHeaderHasInvalidFormat_ThrowsInvalidTokenException() {
        // Act and Assert
        assertThrows(
                InvalidTokenException.class,
                () -> authService.logout("Basic token-invalid")
        );

        verifyNoInteractions(
                jwtService,
                sessionService,
                tokenBlacklistService
        );
    }
    @Test
    void logout_WhenTokenIsValid_BlacklistsTokenAndRevokesSession() {
        // Arrange
        String jwt = "jwt-valido";
        String authHeader = "Bearer " + jwt;
        String jti = "token-id-123";
        String sessionId = "session-id-123";

        Date expiration = new Date(
                System.currentTimeMillis() + 60_000
        );

        when(jwtService.extractJti(jwt))
                .thenReturn(jti);

        when(jwtService.extractSessionId(jwt))
                .thenReturn(sessionId);

        when(jwtService.extractExpiration(jwt))
                .thenReturn(expiration);

        when(sessionService.isSessionActive(sessionId))
                .thenReturn(true);

        // Act
        authService.logout(authHeader);

        // Assert
        verify(jwtService).extractJti(jwt);
        verify(jwtService).extractSessionId(jwt);
        verify(jwtService).extractExpiration(jwt);

        verify(sessionService)
                .isSessionActive(sessionId);

        verify(tokenBlacklistService)
                .blacklistToken(
                        eq(jti),
                        longThat(value -> value > 0)
                );

        verify(sessionService)
                .revokeSession(sessionId);
    }
    @Test
    void logout_WhenSessionIsInactive_ThrowsInvalidTokenException() {
        // Arrange
        String jwt = "jwt-valido";
        String authHeader = "Bearer " + jwt;
        String jti = "token-id-123";
        String sessionId = "session-id-123";

        Date expiration = new Date(
                System.currentTimeMillis() + 60_000
        );

        when(jwtService.extractJti(jwt))
                .thenReturn(jti);

        when(jwtService.extractSessionId(jwt))
                .thenReturn(sessionId);

        when(jwtService.extractExpiration(jwt))
                .thenReturn(expiration);

        when(sessionService.isSessionActive(sessionId))
                .thenReturn(false);

        // Act and Assert
        assertThrows(
                InvalidTokenException.class,
                () -> authService.logout(authHeader)
        );

        verify(sessionService)
                .isSessionActive(sessionId);

        verifyNoInteractions(tokenBlacklistService);

        verify(sessionService, never())
                .revokeSession(anyString());
    }
    @Test
    void logout_WhenTokenHasNoJti_ThrowsInvalidTokenException() {
        // Arrange
        String jwt = "jwt-sin-jti";

        when(jwtService.extractJti(jwt))
                .thenReturn(null);

        when(jwtService.extractSessionId(jwt))
                .thenReturn("session-id-123");

        when(jwtService.extractExpiration(jwt))
                .thenReturn(new Date(System.currentTimeMillis() + 60_000));

        // Act and Assert
        assertThrows(
                InvalidTokenException.class,
                () -> authService.logout("Bearer " + jwt)
        );

        verifyNoInteractions(sessionService, tokenBlacklistService);
    }
    @Test
    void refreshToken_WhenTokenIsValid_ReturnsNewAccessToken() {
        // Arrange
        String refreshToken = "refresh-token-valido";
        String email = "usuario@test.com";
        String sessionId = "session-test-123";
        UUID userId = UUID.randomUUID();

        RefreshTokenRequest request = mock(RefreshTokenRequest.class);
        CredentialEntity credential = mock(CredentialEntity.class);

        when(request.refreshToken())
                .thenReturn(refreshToken);

        when(jwtService.extractUsername(refreshToken))
                .thenReturn(email);

        when(credentialRepository.findByEmailAndDeletedAtIsNull(email))
                .thenReturn(Optional.of(credential));

        when(jwtService.extractSessionId(refreshToken))
                .thenReturn(sessionId);

        when(sessionService.isSessionActive(sessionId))
                .thenReturn(true);

        when(jwtService.isTokenValid(refreshToken, credential))
                .thenReturn(true);

        when(credential.getId())
                .thenReturn(userId);

        when(jwtService.generateToken(credential, userId, sessionId))
                .thenReturn("nuevo-access-token");

        // Act
        AuthResponse response = authService.refreshToken(request);

        // Assert
        assertNotNull(response);
        assertEquals("nuevo-access-token", response.accessToken());
        assertEquals(refreshToken, response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(86_400, response.expiresIn());

        verify(credentialRepository)
                .findByEmailAndDeletedAtIsNull(email);

        verify(sessionService)
                .isSessionActive(sessionId);

        verify(jwtService)
                .isTokenValid(refreshToken, credential);

        verify(jwtService)
                .generateToken(credential, userId, sessionId);

        verify(jwtService, never())
                .generateRefreshToken(any(), any(UUID.class), anyString());

        verify(sessionService, never())
                .createSession(anyString(), anyString());

        verify(sessionService, never())
                .revokeSession(anyString());
    }
    @Test
    void refreshToken_WhenSessionIsInactive_ThrowsInvalidTokenException() {
        // Arrange
        String refreshToken = "refresh-token";
        String email = "usuario@test.com";
        String sessionId = "session-inactiva-123";

        RefreshTokenRequest request = mock(RefreshTokenRequest.class);
        CredentialEntity credential = mock(CredentialEntity.class);

        when(request.refreshToken())
                .thenReturn(refreshToken);

        when(jwtService.extractUsername(refreshToken))
                .thenReturn(email);

        when(credentialRepository.findByEmailAndDeletedAtIsNull(email))
                .thenReturn(Optional.of(credential));

        when(jwtService.extractSessionId(refreshToken))
                .thenReturn(sessionId);

        when(sessionService.isSessionActive(sessionId))
                .thenReturn(false);

        // Act and Assert
        InvalidTokenException exception = assertThrows(
                InvalidTokenException.class,
                () -> authService.refreshToken(request)
        );

        assertEquals(
                "La sesión se encuentra cerrada o revocada.",
                exception.getMessage()
        );

        verify(sessionService)
                .isSessionActive(sessionId);

        verify(jwtService, never())
                .isTokenValid(anyString(), any());

        verify(jwtService, never())
                .generateToken(any(), any(UUID.class), anyString());

        verify(jwtService, never())
                .generateRefreshToken(any(), any(UUID.class), anyString());

        verify(sessionService, never())
                .createSession(anyString(), anyString());
    }
    @Test
    void refreshToken_WhenTokenCannotBeParsed_ThrowsInvalidTokenException() {
        // Arrange
        String refreshToken = "refresh-token-malformado";

        RefreshTokenRequest request = mock(RefreshTokenRequest.class);

        when(request.refreshToken())
                .thenReturn(refreshToken);

        when(jwtService.extractUsername(refreshToken))
                .thenThrow(new IllegalArgumentException("JWT malformado"));

        // Act and Assert
        InvalidTokenException exception = assertThrows(
                InvalidTokenException.class,
                () -> authService.refreshToken(request)
        );

        assertEquals(
                "Refresh token inválido.",
                exception.getMessage()
        );

        verify(jwtService)
                .extractUsername(refreshToken);

        verifyNoMoreInteractions(jwtService);

        verifyNoInteractions(
                credentialRepository,
                sessionService,
                tokenBlacklistService
        );
    }
    @Test
    void refreshToken_WhenUserDoesNotExist_ThrowsInvalidTokenException() {
        // Arrange
        String refreshToken = "refresh-token";
        String email = "inexistente@test.com";

        RefreshTokenRequest request = mock(RefreshTokenRequest.class);

        when(request.refreshToken())
                .thenReturn(refreshToken);

        when(jwtService.extractUsername(refreshToken))
                .thenReturn(email);

        when(credentialRepository.findByEmailAndDeletedAtIsNull(email))
                .thenReturn(Optional.empty());

        // Act and Assert
        InvalidTokenException exception = assertThrows(
                InvalidTokenException.class,
                () -> authService.refreshToken(request)
        );

        assertEquals(
                "El usuario asociado al refresh token no existe.",
                exception.getMessage()
        );

        verify(credentialRepository)
                .findByEmailAndDeletedAtIsNull(email);

        verify(jwtService)
                .extractUsername(refreshToken);

        verifyNoMoreInteractions(jwtService);

        verifyNoInteractions(
                sessionService,
                tokenBlacklistService
        );
    }
    @Test
    void refreshToken_WhenTokenValidationFails_ThrowsInvalidTokenException() {
        // Arrange
        String refreshToken = "refresh-token-rechazado";
        String email = "usuario@test.com";
        String sessionId = "session-test-123";

        RefreshTokenRequest request = mock(RefreshTokenRequest.class);
        CredentialEntity credential = mock(CredentialEntity.class);

        when(request.refreshToken())
                .thenReturn(refreshToken);

        when(jwtService.extractUsername(refreshToken))
                .thenReturn(email);

        when(credentialRepository.findByEmailAndDeletedAtIsNull(email))
                .thenReturn(Optional.of(credential));

        when(jwtService.extractSessionId(refreshToken))
                .thenReturn(sessionId);

        when(sessionService.isSessionActive(sessionId))
                .thenReturn(true);

        when(jwtService.isTokenValid(refreshToken, credential))
                .thenReturn(false);

        // Act and Assert
        InvalidTokenException exception = assertThrows(
                InvalidTokenException.class,
                () -> authService.refreshToken(request)
        );

        assertEquals(
                "Refresh token inválido, expirado o revocado.",
                exception.getMessage()
        );

        verify(sessionService)
                .isSessionActive(sessionId);

        verify(jwtService)
                .isTokenValid(refreshToken, credential);

        verify(jwtService, never())
                .generateToken(any(), any(UUID.class), anyString());

        verify(jwtService, never())
                .generateRefreshToken(any(), any(UUID.class), anyString());

        verify(sessionService, never())
                .createSession(anyString(), anyString());

        verify(sessionService, never())
                .revokeSession(anyString());
    }
}