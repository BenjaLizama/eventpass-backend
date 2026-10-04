package cl.eventpass.ms_events.config;

import cl.eventpass.ms_events.dto.response.StandardErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    private static final String[] WHITE_LIST_URL = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(req -> req
                        .requestMatchers(WHITE_LIST_URL)
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/events/**"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/venues/**"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/events/*/reserve",
                                "/api/v1/events/*/release"
                        )
                        .hasRole("INTERNAL_SERVICE")

                        .anyRequest()
                        .authenticated()
                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(
                                authenticationEntryPoint()
                        )
                        .accessDeniedHandler(
                                accessDeniedHandler()
                        )
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {

            StandardErrorResponse error =
                    StandardErrorResponse.builder()
                            .status(
                                    HttpStatus.UNAUTHORIZED.value()
                            )
                            .code("UNAUTHORIZED")
                            .error(
                                    HttpStatus.UNAUTHORIZED
                                            .getReasonPhrase()
                            )
                            .message(
                                    "Se requiere autenticación para acceder a este recurso."
                            )
                            .developerMessage(
                                    authException.getMessage()
                            )
                            .path(request.getRequestURI())
                            .timestamp(
                                    System.currentTimeMillis()
                            )
                            .build();

            response.setStatus(
                    HttpServletResponse.SC_UNAUTHORIZED
            );
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            objectMapper.writeValue(
                    response.getOutputStream(),
                    error
            );
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {

            StandardErrorResponse error =
                    StandardErrorResponse.builder()
                            .status(
                                    HttpStatus.FORBIDDEN.value()
                            )
                            .code("ACCESS_DENIED")
                            .error(
                                    HttpStatus.FORBIDDEN
                                            .getReasonPhrase()
                            )
                            .message(
                                    "No tienes permisos suficientes para realizar esta operación."
                            )
                            .developerMessage(
                                    accessDeniedException.getMessage()
                            )
                            .path(request.getRequestURI())
                            .timestamp(
                                    System.currentTimeMillis()
                            )
                            .build();

            response.setStatus(
                    HttpServletResponse.SC_FORBIDDEN
            );
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            objectMapper.writeValue(
                    response.getOutputStream(),
                    error
            );
        };
    }
}
