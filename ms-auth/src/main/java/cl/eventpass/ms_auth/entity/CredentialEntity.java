package cl.eventpass.ms_auth.entity;

import cl.eventpass.ms_auth.enums.Role;
import jakarta.annotation.Nonnull;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Getter @Setter
@Table(name = "credentials", indexes = {
        @Index(name = "idx_credential_email", columnList = "email")
})
public class CredentialEntity extends BaseEntity implements UserDetails {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Builder.Default
    @Column(name = "is_account_non_locked", nullable = false)
    private boolean accountNonLocked = true;

    // --- Metodos de Spring Security UserDetails ---

    @Override
    @Nonnull
    public Collection<SimpleGrantedAuthority> getAuthorities() {
        return role.getAuthorities();
    }

    @Override
    @Nonnull
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}
