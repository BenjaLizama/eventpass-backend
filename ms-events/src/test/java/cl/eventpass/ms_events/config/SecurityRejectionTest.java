package cl.eventpass.ms_events.config;
import cl.eventpass.ms_events.config.resolver.CurrentUserIdArgumentResolver;
import cl.eventpass.ms_events.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SecurityRejectionTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void serviceNamesCannotBecomeUserIds(){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("eventpass-ms-orders",null,List.of()));
        assertThrows(AccessDeniedException.class,()->new CurrentUserIdArgumentResolver().resolveArgument(null,null,null,null));
    }
    @Test void ownershipDenialIsForbiddenRatherThanServerError(){
        var response=new GlobalExceptionHandler().handleAuthorizationDenied(new AccessDeniedException("Private ownership detail"),new MockHttpServletRequest());
        assertEquals(403,response.getStatusCode().value());
        assertNull(response.getBody().developerMessage());
    }
}
