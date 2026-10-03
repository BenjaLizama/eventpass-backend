package cl.eventpass.ms_auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "application.security.services.ms-orders")
public class MsOrdersClientProperties {

    private String clientId;

    private String clientSecret;
}
