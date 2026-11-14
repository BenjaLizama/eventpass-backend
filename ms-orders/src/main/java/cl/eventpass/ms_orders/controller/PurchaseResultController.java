package cl.eventpass.ms_orders.controller;

import cl.eventpass.ms_orders.enums.OrderStatus;
import cl.eventpass.ms_orders.enums.PaymentStatus;
import cl.eventpass.ms_orders.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
@RequestMapping("/internal/purchases")
public class PurchaseResultController {

    private final JdbcClient jdbc;
    private final OrderRepository orders;

    @PutMapping("/{id}/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finish(@PathVariable UUID id, @RequestBody Outcome outcome) {
        OrderStatus status = outcome == Outcome.ISSUED ? OrderStatus.PAID : OrderStatus.REJECTED_NO_CAPACITY;
        PaymentStatus payment = outcome == Outcome.ISSUED ? PaymentStatus.APPROVED : PaymentStatus.REFUNDED;
        int updated = jdbc.sql("""
                UPDATE orders SET status = :status, payment_status = :payment,
                    updated_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE id = :id AND status = 'PENDING_ISSUANCE'
                """)
                .param("status", status.name()).param("payment", payment.name()).param("id", id).update();
        if (updated == 0 && orders.findById(id).filter(order ->
                order.getStatus() == status && order.getPaymentStatus() == payment).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La orden no admite este resultado.");
        }
    }

    public enum Outcome { ISSUED, NO_CAPACITY }
}
