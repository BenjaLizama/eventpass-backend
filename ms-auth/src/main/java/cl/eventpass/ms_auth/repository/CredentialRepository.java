package cl.eventpass.ms_auth.repository;

import cl.eventpass.ms_auth.entity.CredentialEntity;
import cl.eventpass.ms_auth.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CredentialRepository extends JpaRepository<CredentialEntity, UUID> {

    @Query("SELECT c FROM CredentialEntity c WHERE c.email = :email AND c.deletedAt IS NULL")
    Optional<CredentialEntity> findByEmailActive(@Param("email") String email);

    Optional<CredentialEntity> findByEmail(String email);

    boolean existsByEmailAndDeletedAtIsNull(String email);
    boolean existsByRole(Role role);
}
