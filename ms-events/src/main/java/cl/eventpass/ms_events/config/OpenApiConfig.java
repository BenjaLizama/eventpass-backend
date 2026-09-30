package cl.eventpass.ms_events.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI msEventsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EventPass Events API")
                        .version("1.0.0")
                        .description(
                                "API de gestión de recintos, catálogo y ciclo de vida de eventos en EventPass. " +
                                        "Proporciona la creación y actualización de recintos (venues), administración " +
                                        "de eventos por organizadores y administradores, actualización de estados " +
                                        "(publicado/cancelado) y consulta del catálogo público."
                        )
                        .contact(new Contact()
                                .name("EventPass")
                        )
                )
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(
                                SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name("Authorization")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                );
    }
}
