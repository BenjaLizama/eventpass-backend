package cl.eventpass.ms_events.config;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class SqsConfig {

    @Bean
    public SqsTemplate sqsTemplate(
            SqsAsyncClient sqsAsyncClient,
            JsonMapper jsonMapper
    ) {
        return SqsTemplate.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configureDefaultConverter(converter -> {
                })
                .build();
    }
}
