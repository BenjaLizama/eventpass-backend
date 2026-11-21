package cl.eventpass.ms_events.config;
import cl.eventpass.ms_events.entity.EventEntity;
import cl.eventpass.ms_events.enums.EventStatus;
import cl.eventpass.ms_events.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class EventVisibilityTest {
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void onlyOwnerOrAdminCanReadDraft(){
        EventEntity event=mock(EventEntity.class); UUID owner=UUID.randomUUID();
        when(event.getStatus()).thenReturn(EventStatus.DRAFT);
        assertThrows(ResourceNotFoundException.class,()->EventVisibility.requireVisible(event));
        when(event.getOrganizerId()).thenReturn(owner);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(),null,List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))));
        assertThrows(ResourceNotFoundException.class,()->EventVisibility.requireVisible(event));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner.toString(),null,List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))));
        assertDoesNotThrow(()->EventVisibility.requireVisible(event));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(),null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertDoesNotThrow(()->EventVisibility.requireVisible(event));
    }
}
