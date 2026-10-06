package cl.eventpass.ms_tickets.service.impl;

import cl.eventpass.ms_tickets.document.TicketDocument;
import cl.eventpass.ms_tickets.dto.response.TicketResponse;
import cl.eventpass.ms_tickets.enums.TicketStatus;
import cl.eventpass.ms_tickets.event.OrderCompletedEvent;
import cl.eventpass.ms_tickets.event.OrderCompletedItem;
import cl.eventpass.ms_tickets.exception.BusinessRuleException;
import cl.eventpass.ms_tickets.exception.ResourceNotFoundException;
import cl.eventpass.ms_tickets.mapper.TicketMapper;
import cl.eventpass.ms_tickets.repository.TicketRepository;
import cl.eventpass.ms_tickets.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketMapper ticketMapper;

    @Override
    public void generateTickets(OrderCompletedEvent event) {

        for (OrderCompletedItem item : event.items()) {

            for (int ticketIndex = 0;
                 ticketIndex < item.quantity();
                 ticketIndex++) {

                boolean alreadyExists =
                        ticketRepository
                                .findByOrderItemIdAndTicketIndexAndDeletedAtIsNull(
                                        item.orderItemId(),
                                        ticketIndex
                                )
                                .isPresent();

                if (alreadyExists) {

                    log.debug(
                            "Ticket ya existente. orderItemId={}, ticketIndex={}",
                            item.orderItemId(),
                            ticketIndex
                    );

                    continue;
                }

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

                try {

                    ticketRepository.save(ticket);

                    log.debug(
                            "Ticket generado correctamente. orderId={}, orderItemId={}, ticketIndex={}",
                            event.orderId(),
                            item.orderItemId(),
                            ticketIndex
                    );

                } catch (DuplicateKeyException exception) {

                    /*
                     * Otro consumidor pudo haber creado el mismo ticket
                     * entre el find() y el save().
                     *
                     * El índice único de MongoDB garantiza que solamente
                     * uno de los documentos sea persistido.
                     *
                     * En este caso simplemente ignoramos el duplicado,
                     * haciendo el procesamiento idempotente.
                     */
                    log.debug(
                            "Ticket duplicado detectado. orderItemId={}, ticketIndex={}",
                            item.orderItemId(),
                            ticketIndex
                    );
                }
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
                ticketRepository.findByUserIdAndDeletedAtIsNull(userId);

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
                ticketRepository
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
                ticketRepository.markAsUsed(ticketCode);

        if (updatedTicket != null) {
            return ticketMapper.toResponse(updatedTicket);
        }

        TicketDocument existingTicket =
                ticketRepository
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
