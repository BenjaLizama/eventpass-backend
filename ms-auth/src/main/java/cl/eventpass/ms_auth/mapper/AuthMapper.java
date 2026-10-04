package cl.eventpass.ms_auth.mapper;

import cl.eventpass.ms_auth.dto.request.RegisterRequest;
import cl.eventpass.ms_auth.entity.CredentialEntity;
import cl.eventpass.ms_auth.enums.Role;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public CredentialEntity toEntity(RegisterRequest request, String encodedPassword) {

        return CredentialEntity.builder()
                .email(request.email())
                .password(encodedPassword)
                .role(Role.CUSTOMER)
                .build();
    }
}
