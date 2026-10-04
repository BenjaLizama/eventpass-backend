package cl.eventpass.ms_events.service;

import cl.eventpass.ms_events.dto.response.TicketCategoryResponse;

import java.util.UUID;

public interface TicketCategoryService {
    TicketCategoryResponse getTicketCategory(UUID eventId, UUID ticketCategoryId);
}
