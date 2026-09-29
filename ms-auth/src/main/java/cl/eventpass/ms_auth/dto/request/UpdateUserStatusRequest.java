package cl.eventpass.ms_auth.dto.request;

import cl.eventpass.ms_auth.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        UserStatus status
) {
}
