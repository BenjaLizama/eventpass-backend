package cl.eventpass.ms_auth.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Permission {

    // --- USUARIOS ---
    USER_READ("user:read"),
    USER_UPDATE("user:update"),
    USER_DELETE("user:delete"),

    // --- GESTION DE EVENTOS Y RECINTOS ---
    EVENT_READ("event:read"),
    EVENT_CREATE("event:create"),
    EVENT_UPDATE("event:update"),
    EVENT_DELETE("event:delete"),

    // --- TICKETS Y COMPRAS ---
    TICKET_READ("ticket:read"),
    TICKET_BUY("ticket:buy"),
    TICKET_VALIDATE("ticket:validate"),
    TICKET_REFUND("ticket:refund"),

    // --- METRICAS Y CONFIGURACION ---
    ANALYTICS_READ("analytics:read"),
    SYSTEM_CONFIG("system:config");


    @Getter
    private final String permission;
}
