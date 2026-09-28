package cl.eventpass.ms_auth.dto.response;

import cl.eventpass.ms_auth.entity.CredentialEntity;
import cl.eventpass.ms_auth.enums.Role;

public record UserResponse(
        String email,
        Role role,
        boolean accountNonLocked
) {

    public static UserResponse from(CredentialEntity credential) {
        return new UserResponse(
                credential.getEmail(),
                credential.getRole(),
                credential.isAccountNonLocked()
        );
    }
}
