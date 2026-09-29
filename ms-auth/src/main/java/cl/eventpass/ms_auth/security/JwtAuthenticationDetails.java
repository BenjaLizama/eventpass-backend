package cl.eventpass.ms_auth.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class JwtAuthenticationDetails {

    private final String sessionId;
}
