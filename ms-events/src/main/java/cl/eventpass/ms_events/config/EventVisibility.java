package cl.eventpass.ms_events.config;

import cl.eventpass.ms_events.entity.EventEntity;
import cl.eventpass.ms_events.enums.EventStatus;
import cl.eventpass.ms_events.exception.ResourceNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class EventVisibility {
    private EventVisibility() {}
    public static void requireVisible(EventEntity event) {
        if (event.getDeletedAt() == null && event.getStatus() == EventStatus.PUBLISHED) return;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (event.getDeletedAt() == null && authentication != null) {
            boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            boolean organizer = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ORGANIZER".equals(a.getAuthority()));
            if (admin || (organizer && event.getOrganizerId().toString().equals(authentication.getName()))) return;
        }
        throw new ResourceNotFoundException("Evento no encontrado.");
    }
}
