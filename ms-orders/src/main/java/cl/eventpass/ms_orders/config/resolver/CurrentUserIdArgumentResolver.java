package cl.eventpass.ms_orders.config.resolver;

import cl.eventpass.ms_orders.config.annotation.CurrentUserId;
import jakarta.annotation.Nonnull;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

public class CurrentUserIdArgumentResolver
        implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && parameter.getParameterType().equals(UUID.class);
    }

    @Override
    public Object resolveArgument(
            @Nonnull MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            @Nonnull NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            throw new org.springframework.security.access.AccessDeniedException("Se requiere una identidad de usuario.");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException invalid) {
            throw new org.springframework.security.access.AccessDeniedException("Se requiere una identidad de usuario válida.");
        }
    }
}
