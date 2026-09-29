package cl.eventpass.ms_auth.service;

import cl.eventpass.ms_auth.dto.request.LoginRequest;
import cl.eventpass.ms_auth.dto.request.RefreshTokenRequest;
import cl.eventpass.ms_auth.dto.request.RegisterRequest;
import cl.eventpass.ms_auth.dto.response.AuthResponse;
import cl.eventpass.ms_auth.dto.response.UserResponse;
import cl.eventpass.ms_auth.entity.CredentialEntity;
import cl.eventpass.ms_auth.exception.EmailAlreadyExistsException;
import cl.eventpass.ms_auth.exception.InvalidTokenException;
import cl.eventpass.ms_auth.exception.ResourceNotFoundException;
import cl.eventpass.ms_auth.mapper.AuthMapper;
import cl.eventpass.ms_auth.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final AuthMapper authMapper;
    private final SessionService sessionService;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        return credentialRepository.findByEmailActive(email)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el recurso solicitado."));

    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (credentialRepository.findByEmailActive(request.email()).isPresent()) {
            throw new EmailAlreadyExistsException(request.email());
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        CredentialEntity credential = authMapper.toEntity(request, encodedPassword);
        credentialRepository.save(credential);

        return createAuthResponse(credential);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        UserDetails user = userDetailsService.loadUserByUsername(request.email());
        return createAuthResponse(user);
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {

        String refreshToken = request.refreshToken();

        String userEmail = jwtService.extractUsername(refreshToken);

        if (userEmail == null) {
            throw new InvalidTokenException(
                    "Refresh token malformado o sin usuario asignado."
            );
        }

        UserDetails userDetails =
                userDetailsService.loadUserByUsername(userEmail);

        if (!jwtService.isTokenValid(refreshToken, userDetails)) {
            throw new InvalidTokenException(
                    "Refresh token inválido, expirado o revocado."
            );
        }

        String sessionId =
                jwtService.extractSessionId(refreshToken);

        String newAccessToken =
                jwtService.generateToken(
                        userDetails,
                        sessionId
                );

        return AuthResponse.of(
                newAccessToken,
                refreshToken,
                jwtExpiration / 1000
        );
    }

    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException(
                    "Cabecera Authorization ausente o con formato incorrecto."
            );
        }

        String jwt = authHeader.substring(7);

        String jti = jwtService.extractJti(jwt);
        String sessionId = jwtService.extractSessionId(jwt);
        Date expiration = jwtService.extractExpiration(jwt);

        if (!sessionService.isSessionActive(sessionId)) {
            throw new InvalidTokenException(
                    "La sesión ya se encuentra cerrada o revocada."
            );
        }

        long remainingMillis =
                expiration.getTime() - System.currentTimeMillis();

        if (remainingMillis > 0) {
            tokenBlacklistService.blacklistToken(
                    jti,
                    remainingMillis
            );
        }

        sessionService.revokeSession(sessionId);
    }

    public AuthResponse createAuthResponse(UserDetails user) {

        String sessionId = UUID.randomUUID().toString();

        sessionService.createSession(
                sessionId,
                user.getUsername()
        );

        String accessToken =
                jwtService.generateToken(user, sessionId);

        String refreshToken =
                jwtService.generateRefreshToken(user, sessionId);

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtExpiration / 1000
        );
    }
}
