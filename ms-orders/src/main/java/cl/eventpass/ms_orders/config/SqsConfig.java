package cl.eventpass.ms_orders.config;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Configuration
public class SqsConfig {

    @Bean
    public SqsTemplate sqsTemplate(
            SqsAsyncClient sqsAsyncClient
    ) {
        return SqsTemplate.newTemplate(sqsAsyncClient);
    }
}
