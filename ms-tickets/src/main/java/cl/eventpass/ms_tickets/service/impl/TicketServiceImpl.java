package cl.eventpass.ms_tickets.service.impl;

import cl.eventpass.ms_tickets.document.TicketDocument;
import cl.eventpass.ms_tickets.dto.response.TicketResponse;
import cl.eventpass.ms_tickets.enums.TicketStatus;
import cl.eventpass.ms_tickets.event.OrderCompletedEvent;
import cl.eventpass.ms_tickets.event.OrderCompletedItem;
import cl.eventpass.ms_tickets.mapper.TicketMapper;
import cl.eventpass.ms_tickets.repository.TicketRepository;
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

    private final TicketRepository ticketRepository;
    private final TicketMapper ticketMapper;

    @Override
    public void generateTickets(OrderCompletedEvent event) {
        for (OrderCompletedItem item : event.items()) {
            for (int ticketIndex = 0; ticketIndex < item.quantity(); ticketIndex++) {
                boolean alreadyExists =
                        ticketRepository
                                .findByOrderItemIdAndTicketIndexAndDeletedAtIsNull(
                                        item.orderItemId(),
                                        ticketIndex
                                )
                                .isPresent();

                if (alreadyExists) {
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

                TicketDocument savedTicket = ticketRepository.save(ticket);
            }
        }

        log.info(
                "Generación de tickets finalizada. orderId={}",
                event.orderId()
        );
    }

    @Override
    public List<TicketResponse> getMyTickets(UUID userId) {
        List<TicketDocument> tickets = ticketRepository.findByUserIdAndDeletedAtIsNull(userId);

        return tickets.stream()
                .map(ticketMapper::toResponse)
                .toList();
    }

    private String generateTicketCode() {
        return UUID.randomUUID().toString();
    }
}
