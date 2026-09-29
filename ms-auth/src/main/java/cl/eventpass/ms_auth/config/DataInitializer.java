package cl.eventpass.ms_auth.config;

import cl.eventpass.ms_auth.entity.CredentialEntity;
import cl.eventpass.ms_auth.enums.Role;
import cl.eventpass.ms_auth.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${application.security.admin.email}")
    private String adminEmail;

    @Value("${application.security.admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {

        boolean adminExists =
                credentialRepository.existsByRole(Role.ADMIN);

        if (adminExists) {
            return;
        }

        CredentialEntity admin = CredentialEntity.builder()
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .build();

        credentialRepository.save(admin);
    }
}
