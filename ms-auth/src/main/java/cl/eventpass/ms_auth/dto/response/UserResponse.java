package cl.eventpass.ms_auth.dto.response;

import cl.eventpass.ms_auth.entity.CredentialEntity;
import cl.eventpass.ms_auth.enums.Role;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        Role role,
        boolean accountNonLocked
) {

    public static UserResponse from(CredentialEntity credential) {
        return new UserResponse(
                credential.getId(),
                credential.getEmail(),
                credential.getRole(),
                credential.isAccountNonLocked()
        );
    }
}
