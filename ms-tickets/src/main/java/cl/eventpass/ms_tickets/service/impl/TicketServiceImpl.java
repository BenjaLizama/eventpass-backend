package cl.eventpass.ms_tickets.service.impl;

import cl.eventpass.ms_tickets.document.TicketDocument;
import cl.eventpass.ms_tickets.dto.response.TicketResponse;
import cl.eventpass.ms_tickets.enums.TicketStatus;
import cl.eventpass.ms_tickets.event.OrderCompletedEvent;
import cl.eventpass.ms_tickets.event.OrderCompletedItem;
import cl.eventpass.ms_tickets.exception.BusinessRuleException;
import cl.eventpass.ms_tickets.exception.ResourceNotFoundException;
import cl.eventpass.ms_tickets.mapper.TicketMapper;
import cl.eventpass.ms_tickets.repository.TicketStore;
import cl.eventpass.ms_tickets.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketStore ticketStore;
    private final TicketMapper ticketMapper;

    @Override
    public void generateTickets(OrderCompletedEvent event) {

        for (OrderCompletedItem item : event.items()) {

            for (int ticketIndex = 0;
                 ticketIndex < item.quantity();
                 ticketIndex++) {

                TicketDocument ticket =
                        TicketDocument.builder()
                                .orderId(event.orderId())
                                .orderItemId(item.orderItemId())
                                .ticketIndex(ticketIndex)
                                .userId(event.userId())
                                .eventId(item.eventId())
                                .ticketCategoryId(item.ticketCategoryId())
                                .ticketCode(generateTicketCode())
                                .status(TicketStatus.ACTIVE)
                                .build();

                ticketStore.insertIfAbsent(ticket);
            }
        }

        log.info(
                "Generación de tickets finalizada. orderId={}",
                event.orderId()
        );
    }

    @Override
    public List<TicketResponse> getMyTickets(UUID userId) {

        List<TicketDocument> tickets =
                ticketStore.findByUserIdAndDeletedAtIsNull(userId);

        return tickets.stream()
                .map(ticketMapper::toResponse)
                .toList();
    }

    @Override
    public TicketResponse getTicketByCode(
            String ticketCode,
            UUID userId
    ) {

        TicketDocument ticket =
                ticketStore
                        .findByTicketCodeAndDeletedAtIsNull(ticketCode)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "No se encontró un ticket con el código solicitado."
                                )
                        );

        if (!ticket.getUserId().equals(userId)) {

            throw new ResourceNotFoundException(
                    "No se encontró un ticket con el código solicitado."
            );
        }

        return ticketMapper.toResponse(ticket);
    }

    @Override
    public TicketResponse useTicket(String ticketCode) {

        TicketDocument updatedTicket =
                ticketStore.markAsUsed(ticketCode);

        if (updatedTicket != null) {
            return ticketMapper.toResponse(updatedTicket);
        }

        TicketDocument existingTicket =
                ticketStore
                        .findByTicketCodeAndDeletedAtIsNull(ticketCode)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "No se encontró un ticket con el código solicitado."
                                )
                        );

        throw new BusinessRuleException(
                "El ticket no puede utilizarse porque su estado actual es "
                        + existingTicket.getStatus() + "."
        );
    }


    private String generateTicketCode() {
        return UUID.randomUUID().toString();
    }
}
