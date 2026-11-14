package cl.eventpass.ms_tickets.controller;

import cl.eventpass.ms_tickets.event.OrderCompletedEvent;
import cl.eventpass.ms_tickets.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
@RequestMapping("/internal/tickets")
public class TicketIssuanceController {

    private final TicketService tickets;

    @PostMapping("/issue")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void issue(@RequestBody OrderCompletedEvent order) {
        tickets.generateTickets(order);
    }
}
