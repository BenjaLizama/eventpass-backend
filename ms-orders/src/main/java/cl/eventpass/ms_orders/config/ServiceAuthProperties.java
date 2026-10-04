package cl.eventpass.ms_orders.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.services.auth")
public class ServiceAuthProperties {

    private String baseUrl;
    private String clientId;
    private String clientSecret;
}
