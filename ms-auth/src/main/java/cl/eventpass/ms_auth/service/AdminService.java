package cl.eventpass.ms_auth.service;

import cl.eventpass.ms_auth.dto.request.CreateUserRequest;
import cl.eventpass.ms_auth.dto.request.UpdateUserRequest;
import cl.eventpass.ms_auth.dto.request.UpdateUserStatusRequest;
import cl.eventpass.ms_auth.dto.response.UserResponse;
import cl.eventpass.ms_auth.entity.CredentialEntity;
import cl.eventpass.ms_auth.enums.Role;
import cl.eventpass.ms_auth.exception.EmailAlreadyExistsException;
import cl.eventpass.ms_auth.exception.InvalidRequestException;
import cl.eventpass.ms_auth.exception.ResourceConflictException;
import cl.eventpass.ms_auth.exception.ResourceNotFoundException;
import cl.eventpass.ms_auth.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        return credentialRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() ->
                        new ResourceNotFoundException("No se encontró el usuario solicitado.")
                );
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {

        if (request.email() == null && request.role() == null) {
            throw new InvalidRequestException(
                    "Debe proporcionar al menos un campo para actualizar."
            );
        }

        CredentialEntity credential = credentialRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No se encontró el usuario solicitado."
                        )
                );

        if (request.email() != null &&
                !request.email().equals(credential.getEmail())) {

            credentialRepository.findByEmail(request.email())
                    .ifPresent(existing -> {
                        throw new ResourceConflictException(
                                "El correo electrónico ya se encuentra registrado."
                        );
                    });

            credential.setEmail(request.email());
        }

        if (request.role() != null) {
            credential.setRole(request.role());
        }

        CredentialEntity updatedCredential =
                credentialRepository.save(credential);

        return UserResponse.from(updatedCredential);
    }

    @Transactional
    public UserResponse updateUserStatus(
            UUID id,
            UpdateUserStatusRequest request
    ) {
        CredentialEntity credential =
                credentialRepository.findByIdAndDeletedAtIsNull(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "No se encontró el usuario solicitado."
                                )
                        );

        credential.setStatus(request.status());

        CredentialEntity updatedCredential =
                credentialRepository.save(credential);

        return UserResponse.from(updatedCredential);
    }

    public List<UserResponse> getAllUsers() {
        return credentialRepository.findAll()
                .stream()
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getEmail(),
                        user.getRole(),
                        user.isAccountNonLocked()
                )).toList();
    }

    private UserResponse createUser(
            CreateUserRequest request,
            Role role
    ) {
        if (credentialRepository.findByEmailAndDeletedAtIsNull(request.email()).isPresent()) {
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
