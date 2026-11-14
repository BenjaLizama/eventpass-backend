package cl.eventpass.ms_orders.enums;

public enum OrderStatus {
    PENDING,
    AWAITING_PAYMENT,
    PENDING_ISSUANCE,
    REJECTED_NO_CAPACITY,
    PAID,
    CANCELLED,
    EXPIRED
}
