package cl.eventpass.ms_events.controller;

import cl.eventpass.ms_events.controller.docs.TicketCategoryControllerDocs;
import cl.eventpass.ms_events.dto.response.StandardResponse;
import cl.eventpass.ms_events.dto.response.TicketCategoryResponse;
import cl.eventpass.ms_events.service.TicketCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class TicketCategoryController implements TicketCategoryControllerDocs {

    private final TicketCategoryService ticketCategoryService;

    @Override
    @GetMapping("/{eventId}/ticket-categories/{ticketCategoryId}")
    public ResponseEntity<StandardResponse<TicketCategoryResponse>> getTicketCategory(
            @PathVariable UUID eventId,
            @PathVariable UUID ticketCategoryId
    ) {
        TicketCategoryResponse response =
                ticketCategoryService.getTicketCategory(
                        eventId,
                        ticketCategoryId
                );

        return ResponseEntity.ok(
                StandardResponse.ok(
                        "Categoría de ticket encontrada con éxito.",
                        response
                )
        );
    }
}
