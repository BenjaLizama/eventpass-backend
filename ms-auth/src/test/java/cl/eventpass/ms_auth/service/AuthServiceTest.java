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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

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

}