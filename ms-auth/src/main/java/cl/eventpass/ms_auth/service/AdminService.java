package cl.eventpass.ms_auth.service;

import cl.eventpass.ms_auth.dto.request.CreateUserRequest;
import cl.eventpass.ms_auth.dto.response.UserResponse;
import cl.eventpass.ms_auth.entity.CredentialEntity;
import cl.eventpass.ms_auth.enums.Role;
import cl.eventpass.ms_auth.exception.EmailAlreadyExistsException;
import cl.eventpass.ms_auth.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createAdmin(CreateUserRequest request) {
        return createUser(request, Role.ADMIN);
    }

    @Transactional
    public UserResponse createStaff(CreateUserRequest request) {
        return createUser(request, Role.STAFF);
    }

    @Transactional
    public UserResponse createOrganizer(CreateUserRequest request) {
        return createUser(request, Role.ORGANIZER);
    }

    @Transactional
    public UserResponse createSupport(CreateUserRequest request) {
        return createUser(request, Role.SUPPORT);
    }

    private UserResponse createUser(
            CreateUserRequest request,
            Role role
    ) {
        if (credentialRepository.findByEmailActive(request.email()).isPresent()) {
            throw new EmailAlreadyExistsException(request.email());
        }

        String encodedPassword =
                passwordEncoder.encode(request.password());

        CredentialEntity credential = CredentialEntity.builder()
                .email(request.email())
                .password(encodedPassword)
                .role(role)
                .build();

        credentialRepository.save(credential);

        return UserResponse.from(credential);
    }
}
