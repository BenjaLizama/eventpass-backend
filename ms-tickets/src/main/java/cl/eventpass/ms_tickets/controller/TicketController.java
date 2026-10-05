package cl.eventpass.ms_tickets.controller;

import cl.eventpass.ms_tickets.config.annotation.CurrentUserId;
import cl.eventpass.ms_tickets.controller.docs.TicketControllerDocs;
import cl.eventpass.ms_tickets.dto.response.StandardResponse;
import cl.eventpass.ms_tickets.dto.response.TicketResponse;
import cl.eventpass.ms_tickets.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController implements TicketControllerDocs {

    private final TicketService ticketService;

    @Override
    @GetMapping("/me")
    public ResponseEntity<StandardResponse<List<TicketResponse>>> getMyTickets(
            @CurrentUserId UUID userId
    ) {
        List<TicketResponse> response = ticketService.getMyTickets(userId);

        return ResponseEntity.ok(StandardResponse.ok(
                "Recursos obtenidos con exito.",
                response
        ));
    }
}
