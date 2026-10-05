package cl.eventpass.ms_tickets.service.impl;

import cl.eventpass.ms_tickets.document.TicketDocument;
import cl.eventpass.ms_tickets.enums.TicketStatus;
import cl.eventpass.ms_tickets.event.OrderCompletedEvent;
import cl.eventpass.ms_tickets.event.OrderCompletedItem;
import cl.eventpass.ms_tickets.repository.TicketRepository;
import cl.eventpass.ms_tickets.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;

    @Override
    public void generateTickets(OrderCompletedEvent event) {

        log.info(
                "Generando tickets. orderId={}, userId={}, items={}",
                event.orderId(),
                event.userId(),
                event.items().size()
        );

        for (OrderCompletedItem item : event.items()) {

            log.info(
                    "Procesando item. orderItemId={}, eventId={}, ticketCategoryId={}, quantity={}",
                    item.orderItemId(),
                    item.eventId(),
                    item.ticketCategoryId(),
                    item.quantity()
            );

            for (int ticketIndex = 0; ticketIndex < item.quantity(); ticketIndex++) {

                log.info(
                        "Procesando ticket. orderItemId={}, ticketIndex={}",
                        item.orderItemId(),
                        ticketIndex
                );

                boolean alreadyExists =
                        ticketRepository
                                .findByOrderItemIdAndTicketIndexAndDeletedAtIsNull(
                                        item.orderItemId(),
                                        ticketIndex
                                )
                                .isPresent();

                if (alreadyExists) {

                    log.info(
                            "Ticket ya existe. orderItemId={}, ticketIndex={}",
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

                log.info(
                        "Guardando ticket. orderItemId={}, ticketIndex={}, ticketCode={}",
                        item.orderItemId(),
                        ticketIndex,
                        ticket.getTicketCode()
                );

                TicketDocument savedTicket = ticketRepository.save(ticket);

                log.info(
                        "Ticket guardado correctamente. id={}, ticketCode={}",
                        savedTicket.getId(),
                        savedTicket.getTicketCode()
                );
            }
        }

        log.info(
                "Generación de tickets finalizada. orderId={}",
                event.orderId()
        );
    }

    private String generateTicketCode() {
        return UUID.randomUUID().toString();
    }
}
