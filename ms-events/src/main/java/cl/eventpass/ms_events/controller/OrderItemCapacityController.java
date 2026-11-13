package cl.eventpass.ms_events.controller;

import cl.eventpass.ms_events.dto.request.CapacityReservationRequest;
import cl.eventpass.ms_events.dto.response.OrderItemReservationResponse;
import cl.eventpass.ms_events.dto.response.StandardResponse;
import cl.eventpass.ms_events.service.OrderItemCapacityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/reservations/{orderItemId}")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
@RequiredArgsConstructor
public class OrderItemCapacityController {

    private final OrderItemCapacityService capacityService;

    @PutMapping
    public StandardResponse<OrderItemReservationResponse> reserve(
            @PathVariable UUID eventId,
            @PathVariable UUID orderItemId,
            @Valid @RequestBody CapacityReservationRequest request
    ) {
        var result = capacityService.reserve(eventId, orderItemId, request.ticketCategoryId(), request.quantity());
        return StandardResponse.ok("Resultado de la reserva.", result);
    }

    @PatchMapping("/release")
    public StandardResponse<OrderItemReservationResponse> release(
            @PathVariable UUID eventId,
            @PathVariable UUID orderItemId
    ) {
        var result = capacityService.release(eventId, orderItemId);
        return StandardResponse.ok("Resultado de la liberación.", result);
    }
}
