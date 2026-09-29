package cl.eventpass.ms_auth.entity;

import cl.eventpass.ms_auth.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class CredentialEntityTest {

    @Test
    void shouldReturnEmailAsUsername() {
        CredentialEntity credential = CredentialEntity.builder()
                .email("user@example.com")
                .build();

        assertEquals("user@example.com", credential.getUsername());
    }

    @Test
    void shouldReturnAuthoritiesFromRole() {
        CredentialEntity credential = CredentialEntity.builder()
                .email("admin@example.com")
                .role(Role.ADMIN)
                .build();

        Collection<SimpleGrantedAuthority> authorities = credential.getAuthorities();

        assertEquals(Role.ADMIN.getAuthorities(), authorities);
    }

    @Test
    void shouldReturnTrueWhenAccountIsNonLockedByDefault() {
        CredentialEntity credential = CredentialEntity.builder()
                .email("user@example.com")
                .role(Role.CUSTOMER)
                .build();

        assertTrue(credential.isAccountNonLocked());
    }

    @Test
    void shouldReturnFalseWhenAccountIsLocked() {
        CredentialEntity credential = CredentialEntity.builder()
                .email("user@example.com")
                .role(Role.CUSTOMER)
                .accountNonLocked(false)
                .build();

        assertFalse(credential.isAccountNonLocked());
    }

    @Test
    void shouldReturnTrueWhenAccountIsNonExpired() {
        CredentialEntity credential = new CredentialEntity();

        assertTrue(credential.isAccountNonExpired());
    }

    @Test
    void shouldReturnTrueWhenCredentialsAreNonExpired() {
        CredentialEntity credential = new CredentialEntity();

        assertTrue(credential.isCredentialsNonExpired());
    }

    @Test
    void shouldReturnTrueWhenAccountIsEnabled() {
        CredentialEntity credential = new CredentialEntity();

        assertTrue(credential.isEnabled());
    }

    @Test
    void shouldSetAndGetCredentialFields() {
        CredentialEntity credential = new CredentialEntity();

        credential.setEmail("user@example.com");
        credential.setPassword("encoded-password");
        credential.setRole(Role.CUSTOMER);
        credential.setAccountNonLocked(false);

        assertEquals("user@example.com", credential.getEmail());
        assertEquals("encoded-password", credential.getPassword());
        assertEquals(Role.CUSTOMER, credential.getRole());
        assertFalse(credential.isAccountNonLocked());
    }
}
