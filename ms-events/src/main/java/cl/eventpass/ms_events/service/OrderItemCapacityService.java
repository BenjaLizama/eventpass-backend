package cl.eventpass.ms_events.service;

import cl.eventpass.ms_events.dto.response.OrderItemReservationResponse;
import cl.eventpass.ms_events.entity.CapacityReservation;
import cl.eventpass.ms_events.enums.CapacityReservationStatus;
import cl.eventpass.ms_events.enums.EventStatus;
import cl.eventpass.ms_events.exception.ResourceConflictException;
import cl.eventpass.ms_events.exception.ResourceNotFoundException;
import cl.eventpass.ms_events.repository.CapacityReservationRepository;
import cl.eventpass.ms_events.repository.EventRepository;
import cl.eventpass.ms_events.repository.TicketCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderItemCapacityService {

    private final CapacityReservationRepository reservationRepository;
    private final EventRepository eventRepository;
    private final TicketCategoryRepository categoryRepository;

    @Transactional
    public OrderItemReservationResponse reserve(
            UUID eventId, UUID orderItemId, UUID ticketCategoryId, int quantity
    ) {
        boolean inserted = reservationRepository.insertIfAbsent(orderItemId, eventId, ticketCategoryId, quantity) == 1;
        CapacityReservation reservation = findReservation(eventId, orderItemId);
        if (!reservation.getTicketCategoryId().equals(ticketCategoryId) || reservation.getQuantity() != quantity) {
            throw new ResourceConflictException("La reserva ya existe con una categoría o cantidad distinta.");
        }

        if (inserted) {
            var event = eventRepository.findActiveForReservation(eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("No se encontró el evento solicitado."));
            categoryRepository.findByIdAndDeletedAtIsNull(ticketCategoryId)
                    .filter(category -> category.getEvent().getId().equals(eventId))
                    .orElseThrow(() -> new ResourceNotFoundException("No se encontró la categoría en el evento."));

            if (event.getStatus() == EventStatus.PUBLISHED
                    && categoryRepository.decrementAvailableCapacity(eventId, ticketCategoryId, quantity) == 1) {
                reservation.markReserved();
            }
        }

        return response(reservation);
    }

    @Transactional
    public OrderItemReservationResponse release(UUID eventId, UUID orderItemId) {
        CapacityReservation reservation = findReservation(eventId, orderItemId);
        if (reservation.getStatus() == CapacityReservationStatus.RESERVED) {
            if (categoryRepository.incrementAvailableCapacity(reservation.getTicketCategoryId(), reservation.getQuantity()) != 1) {
                throw new IllegalStateException("No fue posible restituir el aforo reservado.");
            }
            reservation.markReleased();
        }

        return response(reservation);
    }

    private CapacityReservation findReservation(UUID eventId, UUID orderItemId) {
        CapacityReservation reservation = reservationRepository.findForUpdate(orderItemId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la reserva solicitada."));
        if (!reservation.getEventId().equals(eventId)) {
            throw new ResourceConflictException("La reserva pertenece a otro evento.");
        }
        return reservation;
    }

    private OrderItemReservationResponse response(CapacityReservation reservation) {
        return new OrderItemReservationResponse(
                reservation.getOrderItemId(),
                reservation.getEventId(),
                reservation.getTicketCategoryId(),
                reservation.getQuantity(),
                reservation.getStatus()
        );
    }
}
