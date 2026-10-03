package cl.eventpass.ms_orders.config;

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
    public OpenAPI msOrdersOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EventPass Orders API")
                        .version("1.0.0")
                        .description(
                                "API de gestión de órdenes de compra en EventPass. "
                                        + "Proporciona la creación y gestión del ciclo de vida "
                                        + "de las órdenes, validación de límites de compra por usuario, "
                                        + "reserva y liberación de capacidad de tickets mediante "
                                        + "comunicación interna con ms-events y procesamiento "
                                        + "del estado de pago."
                        )
                        .contact(new Contact()
                                .name("EventPass")
                        )
                )
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(SECURITY_SCHEME_NAME)
                )
                .components(
                        new Components()
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
