package cl.eventpass.ms_auth.repository;

import cl.eventpass.ms_auth.entity.CredentialEntity;
import cl.eventpass.ms_auth.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CredentialRepository
        extends JpaRepository<CredentialEntity, UUID> {

    Optional<CredentialEntity>
    findByEmailAndDeletedAtIsNull(String email);

    Optional<CredentialEntity>
    findByIdAndDeletedAtIsNull(UUID id);

    Optional<CredentialEntity>
    findByEmail(String email);

    boolean existsByRole(Role role);
}