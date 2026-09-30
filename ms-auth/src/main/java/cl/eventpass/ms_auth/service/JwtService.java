package cl.eventpass.ms_auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    private final TokenBlacklistService tokenBlacklistService;
    private final SessionService sessionService;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String extractUserId(String token) {
        return extractClaim(
                token,
                claims -> claims.get("userId", String.class)
        );
    }

    public String extractSessionId(String token) {
        return extractClaim(
                token,
                claims -> claims.get("sid", String.class)
        );
    }

    public <T> T extractClaim(
            String token,
            Function<Claims, T> claimsResolver
    ) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(
            UserDetails userDetails,
            UUID userId,
            String sessionId
    ) {
        Map<String, Object> extraClaims = new HashMap<>();

        extraClaims.put(
                "authorities",
                userDetails.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList()
        );

        extraClaims.put(
                "userId",
                userId.toString()
        );

        extraClaims.put(
                "sid",
                sessionId
        );

        return buildToken(
                extraClaims,
                userDetails,
                jwtExpiration
        );
    }

    public String generateRefreshToken(
            UserDetails userDetails,
            UUID userId,
            String sessionId
    ) {
        Map<String, Object> extraClaims = new HashMap<>();

        extraClaims.put(
                "userId",
                userId.toString()
        );

        extraClaims.put(
                "sid",
                sessionId
        );

        return buildToken(
                extraClaims,
                userDetails,
                refreshExpiration
        );
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        Date now = new Date();

        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(
                        new Date(now.getTime() + expiration)
                )
                .signWith(getSignInKey())
                .compact();
    }

    public boolean isTokenValid(
            String token,
            UserDetails userDetails
    ) {
        final String username = extractUsername(token);
        final String jti = extractJti(token);
        final String sessionId = extractSessionId(token);

        return username.equals(userDetails.getUsername())
                && !isTokenExpired(token)
                && !tokenBlacklistService.isBlacklisted(jti)
                && sessionService.isSessionActive(sessionId);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
