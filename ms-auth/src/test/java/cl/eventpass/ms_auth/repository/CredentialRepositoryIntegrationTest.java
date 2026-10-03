package cl.eventpass.ms_auth.repository;

import cl.eventpass.ms_auth.MsAuthApplication;
import cl.eventpass.ms_auth.entity.CredentialEntity;
import cl.eventpass.ms_auth.enums.Role;
import cl.eventpass.ms_auth.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import java.util.Optional;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Testcontainers
@ContextConfiguration(classes = MsAuthApplication.class)
@Import(CredentialRepositoryIntegrationTest.AuditTestConfig.class)
class CredentialRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("eventpass_auth_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );
        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
        registry.add(
                "spring.jpa.hibernate.ddl-auto",
                () -> "create-drop"
        );
    }

    @Autowired
    private CredentialRepository credentialRepository;

    @Test
    void saveAndFindById_ReturnsPersistedCredential() {
        CredentialEntity credential = createCredential(
                "usuario@test.com",
                Role.CUSTOMER,
                UserStatus.ACTIVE,
                null
        );

        CredentialEntity saved =
                credentialRepository.saveAndFlush(credential);

        Optional<CredentialEntity> result =
                credentialRepository.findById(saved.getId());

        assertTrue(result.isPresent());
        assertEquals("usuario@test.com", result.get().getEmail());
        assertEquals(Role.CUSTOMER, result.get().getRole());
        assertEquals(UserStatus.ACTIVE, result.get().getStatus());
        assertNotNull(result.get().getId());
        assertNotNull(result.get().getCreatedAt());
    }

    @Test
    void findByEmailAndDeletedAtIsNull_WhenCredentialIsActive_ReturnsCredential() {
        CredentialEntity credential = createCredential(
                "activo@test.com",
                Role.CUSTOMER,
                UserStatus.ACTIVE,
                null
        );

        credentialRepository.saveAndFlush(credential);

        Optional<CredentialEntity> result =
                credentialRepository.findByEmailAndDeletedAtIsNull(
                        "activo@test.com"
                );

        assertTrue(result.isPresent());
        assertEquals("activo@test.com", result.get().getEmail());
        assertFalse(result.get().isDeleted());
    }

    @Test
    void findByEmailAndDeletedAtIsNull_WhenCredentialIsDeleted_ReturnsEmpty() {
        CredentialEntity credential = createCredential(
                "eliminado@test.com",
                Role.CUSTOMER,
                UserStatus.ACTIVE,
                Instant.now()
        );

        credentialRepository.saveAndFlush(credential);

        Optional<CredentialEntity> result =
                credentialRepository.findByEmailAndDeletedAtIsNull(
                        "eliminado@test.com"
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void findByEmail_WhenCredentialIsDeleted_ReturnsCredential() {
        CredentialEntity credential = createCredential(
                "eliminado-con-busqueda@test.com",
                Role.CUSTOMER,
                UserStatus.ACTIVE,
                Instant.now()
        );

        credentialRepository.saveAndFlush(credential);

        Optional<CredentialEntity> result =
                credentialRepository.findByEmail(
                        "eliminado-con-busqueda@test.com"
                );

        assertTrue(result.isPresent());
        assertNotNull(result.get().getDeletedAt());
    }

    @Test
    void existsByRole_WhenRoleExists_ReturnsTrue() {
        CredentialEntity credential = createCredential(
                "customer@test.com",
                Role.CUSTOMER,
                UserStatus.ACTIVE,
                null
        );

        credentialRepository.saveAndFlush(credential);

        boolean result =
                credentialRepository.existsByRole(Role.CUSTOMER);

        assertTrue(result);
    }

    @Test
    void existsByRole_WhenRoleDoesNotExist_ReturnsFalse() {
        boolean result =
                credentialRepository.existsByRole(Role.ORGANIZER);

        assertFalse(result);
    }

    @Test
    void save_WhenEmailIsDuplicated_ThrowsDataIntegrityViolationException() {
        String duplicatedEmail = "duplicado@test.com";

        credentialRepository.saveAndFlush(
                createCredential(
                        duplicatedEmail,
                        Role.CUSTOMER,
                        UserStatus.ACTIVE,
                        null
                )
        );

        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> credentialRepository.saveAndFlush(
                        createCredential(
                                duplicatedEmail,
                                Role.STAFF,
                                UserStatus.ACTIVE,
                                null
                        )
                )
        );
    }

    private CredentialEntity createCredential(
            String email,
            Role role,
            UserStatus status,
            Instant deletedAt
    ) {
        CredentialEntity credential = new CredentialEntity();

        credential.setEmail(email);
        credential.setPassword("password-encriptada");
        credential.setRole(role);
        credential.setStatus(status);
        credential.setDeletedAt(deletedAt);

        return credential;
    }
    @Configuration(proxyBeanMethods = false)
    static class AuditTestConfig {

        @Bean(name = "auditorProvider")
        AuditorAware<String> auditorProvider() {
            return () -> Optional.of("test-user");
        }
    }
}