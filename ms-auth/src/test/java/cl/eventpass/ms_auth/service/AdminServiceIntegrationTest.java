package cl.eventpass.ms_auth.service;

import cl.eventpass.ms_auth.dto.request.CreateUserRequest;
import cl.eventpass.ms_auth.dto.response.UserResponse;
import cl.eventpass.ms_auth.entity.CredentialEntity;
import cl.eventpass.ms_auth.enums.Role;
import cl.eventpass.ms_auth.exception.EmailAlreadyExistsException;
import cl.eventpass.ms_auth.repository.CredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class AdminServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("eventpass_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
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
                "spring.datasource.driver-class-name",
                postgres::getDriverClassName
        );
    }

    @Autowired
    private AdminService adminService;

    @Autowired
    private CredentialRepository credentialRepository;

    @BeforeEach
    void setUp() {
        credentialRepository.deleteAll();
    }

    @Test
    void shouldCreateAdmin() {

        CreateUserRequest request = new CreateUserRequest(
                "admin@test.com",
                "Password123!"
        );

        UserResponse response = adminService.createAdmin(request);

        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("admin@test.com");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(response.accountNonLocked()).isTrue();

        CredentialEntity savedUser =
                credentialRepository.findByEmailActive("admin@test.com")
                        .orElseThrow();

        assertThat(savedUser.getEmail())
                .isEqualTo("admin@test.com");

        assertThat(savedUser.getRole())
                .isEqualTo(Role.ADMIN);

        assertThat(savedUser.getPassword())
                .isNotEqualTo("Password123!");
    }

    @Test
    void shouldCreateStaff() {

        CreateUserRequest request = new CreateUserRequest(
                "staff@test.com",
                "Password123!"
        );

        UserResponse response = adminService.createStaff(request);

        assertThat(response.email()).isEqualTo("staff@test.com");
        assertThat(response.role()).isEqualTo(Role.STAFF);

        assertThat(
                credentialRepository.findByEmailActive("staff@test.com")
        ).isPresent();
    }

    @Test
    void shouldCreateOrganizer() {

        CreateUserRequest request = new CreateUserRequest(
                "organizer@test.com",
                "Password123!"
        );

        UserResponse response =
                adminService.createOrganizer(request);

        assertThat(response.email())
                .isEqualTo("organizer@test.com");

        assertThat(response.role())
                .isEqualTo(Role.ORGANIZER);
    }

    @Test
    void shouldCreateSupport() {

        CreateUserRequest request = new CreateUserRequest(
                "support@test.com",
                "Password123!"
        );

        UserResponse response =
                adminService.createSupport(request);

        assertThat(response.email())
                .isEqualTo("support@test.com");

        assertThat(response.role())
                .isEqualTo(Role.SUPPORT);
    }

    @Test
    void shouldNotCreateUserWhenEmailAlreadyExists() {

        CreateUserRequest firstRequest = new CreateUserRequest(
                "duplicate@test.com",
                "Password123!"
        );

        adminService.createAdmin(firstRequest);

        CreateUserRequest secondRequest = new CreateUserRequest(
                "duplicate@test.com",
                "AnotherPassword123!"
        );

        assertThatThrownBy(() ->
                adminService.createStaff(secondRequest)
        )
                .isInstanceOf(EmailAlreadyExistsException.class);

        List<CredentialEntity> users =
                credentialRepository.findAll();

        assertThat(users).hasSize(1);

        assertThat(users.get(0).getRole())
                .isEqualTo(Role.ADMIN);
    }

    @Test
    void shouldGetAllUsers() {

        adminService.createAdmin(
                new CreateUserRequest(
                        "admin@test.com",
                        "Password123!"
                )
        );

        adminService.createStaff(
                new CreateUserRequest(
                        "staff@test.com",
                        "Password123!"
                )
        );

        adminService.createOrganizer(
                new CreateUserRequest(
                        "organizer@test.com",
                        "Password123!"
                )
        );

        List<UserResponse> users =
                adminService.getAllUsers();

        assertThat(users)
                .hasSize(3);

        assertThat(users)
                .extracting(UserResponse::email)
                .containsExactlyInAnyOrder(
                        "admin@test.com",
                        "staff@test.com",
                        "organizer@test.com"
                );

        assertThat(users)
                .extracting(UserResponse::role)
                .containsExactlyInAnyOrder(
                        Role.ADMIN,
                        Role.STAFF,
                        Role.ORGANIZER
                );
    }
}
