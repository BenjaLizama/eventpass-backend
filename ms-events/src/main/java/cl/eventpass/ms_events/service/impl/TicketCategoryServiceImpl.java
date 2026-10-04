package cl.eventpass.ms_events.service.impl;

import cl.eventpass.ms_events.dto.response.TicketCategoryResponse;
import cl.eventpass.ms_events.entity.TicketCategoryEntity;
import cl.eventpass.ms_events.exception.ResourceNotFoundException;
import cl.eventpass.ms_events.repository.TicketCategoryRepository;
import cl.eventpass.ms_events.service.TicketCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketCategoryServiceImpl implements TicketCategoryService {

    private final TicketCategoryRepository ticketCategoryRepository;

    @Override
    @Transactional(readOnly = true)
    public TicketCategoryResponse getTicketCategory(
            UUID eventId,
            UUID ticketCategoryId
    ) {
        TicketCategoryEntity category = ticketCategoryRepository
                .findById(ticketCategoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No se encontró la categoría de ticket solicitada."
                        )
                );

        if (!category.getEvent().getId().equals(eventId)) {
            throw new ResourceNotFoundException(
                    "La categoría de ticket no pertenece al evento indicado."
            );
        }

        return new TicketCategoryResponse(
                category.getId(),
                category.getName(),
                category.getPrice(),
                category.getTotalCapacity(),
                category.getAvailableCapacity(),
                category.getMaxPerUser()
        );
    }
}
