package cl.eventpass.ms_tickets.controller;

import cl.eventpass.ms_tickets.config.annotation.CurrentUserId;
import cl.eventpass.ms_tickets.controller.docs.TicketControllerDocs;
import cl.eventpass.ms_tickets.dto.response.StandardResponse;
import cl.eventpass.ms_tickets.dto.response.TicketResponse;
import cl.eventpass.ms_tickets.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController implements TicketControllerDocs {

    private final TicketService ticketService;

    @Override
    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<StandardResponse<List<TicketResponse>>> getMyTickets(
            @CurrentUserId UUID userId
    ) {
        List<TicketResponse> response = ticketService.getMyTickets(userId);

        return ResponseEntity.ok(StandardResponse.ok(
                "Recursos obtenidos con exito.",
                response
        ));
    }

    @Override
    @GetMapping("/{ticketCode}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<StandardResponse<TicketResponse>> getTicketByCode(
            @PathVariable String ticketCode,
            @CurrentUserId UUID userId
    ) {
        TicketResponse response = ticketService.getTicketByCode(ticketCode, userId);

        return ResponseEntity.ok(StandardResponse.ok(
                "Ticket obtenido con éxito.",
                response
        ));
    }

    @Override
    @PatchMapping("/{ticketCode}/use")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<StandardResponse<TicketResponse>> useTicket(
            @PathVariable String ticketCode
    ) {
        TicketResponse response = ticketService.useTicket(ticketCode);

        return ResponseEntity.ok(StandardResponse.ok(
                "Ticket utilizado con éxito.",
                response
        ));
    }
}
