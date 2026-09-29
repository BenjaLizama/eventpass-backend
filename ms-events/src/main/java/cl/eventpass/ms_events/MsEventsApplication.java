package cl.eventpass.ms_events;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class MsEventsApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsEventsApplication.class, args);
	}

}
