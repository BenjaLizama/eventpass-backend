package cl.eventpass.ms_auth.service;
import cl.eventpass.ms_auth.dto.request.*;
import cl.eventpass.ms_auth.entity.CredentialEntity;
import cl.eventpass.ms_auth.enums.*;
import cl.eventpass.ms_auth.repository.CredentialRepository;
import cl.eventpass.ms_auth.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AdminSecurityTest {
    @Test void blockingAndChangingRolesRevokesExistingSessions(){
        var repo=mock(CredentialRepository.class);var sessions=mock(SessionService.class);
        var admin=new AdminService(repo,mock(PasswordEncoder.class),sessions);
        UUID id=UUID.randomUUID();
        var user=CredentialEntity.builder().id(id).email("owner@example.com").role(Role.ORGANIZER).build();
        when(repo.findById(id)).thenReturn(Optional.of(user));
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(user));
        when(repo.save(user)).thenReturn(user);
        admin.updateUser(id,new UpdateUserRequest(null,Role.CUSTOMER));
        verify(sessions).revokeUserSessions("owner@example.com");
        admin.updateUserStatus(id,new UpdateUserStatusRequest(UserStatus.BLOCKED));
        verify(sessions,times(2)).revokeUserSessions("owner@example.com");
        assertThrows(InvalidRequestException.class,()->admin.updateUser(id,new UpdateUserRequest(null,Role.INTERNAL_SERVICE)));
    }
}
