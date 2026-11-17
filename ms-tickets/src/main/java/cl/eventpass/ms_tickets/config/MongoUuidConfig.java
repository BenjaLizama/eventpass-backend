package cl.eventpass.ms_tickets.config;

import cl.eventpass.ms_tickets.document.BaseDocument;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;

import java.util.UUID;

@Configuration
@Profile("!dynamodb")
public class MongoUuidConfig {

    @Bean
    public BeforeConvertCallback<BaseDocument> uuidCallback() {
        return (document, collection) -> {
            if (document.getId() == null) {
                document.setId(UUID.randomUUID());
            }

            return document;
        };
    }
}
