package cl.eventpass.ms_auth.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public enum Role {

    CUSTOMER(Set.of(
            Permission.USER_READ,
            Permission.USER_UPDATE,
            Permission.EVENT_READ,
            Permission.TICKET_READ,
            Permission.TICKET_BUY
    )),

    STAFF(Set.of(
            Permission.TICKET_VALIDATE
    )),

    ORGANIZER(Set.of(
            Permission.USER_READ,
            Permission.USER_UPDATE,
            Permission.EVENT_READ,
            Permission.EVENT_CREATE,
            Permission.EVENT_UPDATE,
            Permission.EVENT_DELETE,
            Permission.TICKET_READ,
            Permission.ANALYTICS_READ
    )),

    SUPPORT(Set.of(
            Permission.USER_READ,
            Permission.EVENT_READ,
            Permission.TICKET_READ,
            Permission.TICKET_REFUND
    )),

    ADMIN(Set.of(
            Permission.USER_READ,
            Permission.USER_UPDATE,
            Permission.USER_DELETE,
            Permission.EVENT_READ,
            Permission.EVENT_CREATE,
            Permission.EVENT_UPDATE,
            Permission.EVENT_DELETE,
            Permission.TICKET_READ,
            Permission.TICKET_BUY,
            Permission.TICKET_VALIDATE,
            Permission.TICKET_REFUND,
            Permission.ANALYTICS_READ,
            Permission.SYSTEM_CONFIG
    )),

    INTERNAL_SERVICE(Set.of());

    @Getter
    private final Set<Permission> permissions;

    public List<SimpleGrantedAuthority> getAuthorities() {
        List<SimpleGrantedAuthority> authorities = getPermissions().stream()
                .map(permission -> new SimpleGrantedAuthority(permission.getPermission()))
                .collect(Collectors.toList());

        authorities.add(new SimpleGrantedAuthority("ROLE_" + this.name()));
        return authorities;
    }
}
