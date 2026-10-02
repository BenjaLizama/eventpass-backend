package cl.eventpass.ms_orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
public class MsOrdersApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsOrdersApplication.class, args);
	}

}
