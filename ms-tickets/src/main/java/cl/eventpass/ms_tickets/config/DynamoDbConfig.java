package cl.eventpass.ms_tickets.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Configuration
@Profile("dynamodb")
public class DynamoDbConfig {

    @Bean
    DynamoDbClient dynamoDbClient(@Value("${AWS_REGION:us-east-1}") String region,
                                 @Value("${app.tickets.dynamodb.endpoint}") String endpoint) {
        var client = DynamoDbClient.builder().region(Region.of(region));
        if (!endpoint.isBlank()) {
            client.endpointOverride(URI.create(endpoint));
        }
        return client.build();
    }
}
