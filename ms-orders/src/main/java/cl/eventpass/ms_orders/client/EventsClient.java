package cl.eventpass.ms_orders.client;

import cl.eventpass.ms_orders.dto.request.CapacityReleaseRequest;
import cl.eventpass.ms_orders.dto.request.CapacityReservationRequest;
import cl.eventpass.ms_orders.dto.response.CapacityReleaseResponse;
import cl.eventpass.ms_orders.dto.response.CapacityReservationResponse;
import cl.eventpass.ms_orders.dto.response.StandardResponse;
import cl.eventpass.ms_orders.dto.response.TicketCategoryResponse;
import cl.eventpass.ms_orders.security.ServiceTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EventsClient {

    private final RestClient eventsRestClient;
    private final ServiceTokenProvider serviceTokenProvider;

    public CapacityReservationResponse reserveCapacity(
            UUID eventId,
            CapacityReservationRequest request
    ) {

        StandardResponse<CapacityReservationResponse> response =
                eventsRestClient
                        .patch()
                        .uri(
                                "/api/v1/events/{eventId}/reserve",
                                eventId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + serviceTokenProvider.getToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<
                                        StandardResponse<CapacityReservationResponse>
                                        >() {
                                }
                        );

        if (response == null || response.data() == null) {
            throw new IllegalStateException(
                    "ms-events no devolvió una respuesta válida al reservar aforo."
            );
        }

        return response.data();
    }

    public CapacityReleaseResponse releaseCapacity(
            UUID eventId,
            CapacityReleaseRequest request
    ) {

        StandardResponse<CapacityReleaseResponse> response =
                eventsRestClient
                        .patch()
                        .uri(
                                "/api/v1/events/{eventId}/release",
                                eventId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + serviceTokenProvider.getToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<
                                        StandardResponse<CapacityReleaseResponse>
                                        >() {
                                }
                        );

        if (response == null || response.data() == null) {
            throw new IllegalStateException(
                    "ms-events no devolvió una respuesta válida al liberar aforo."
            );
        }

        return response.data();
    }

    public TicketCategoryResponse getTicketCategory(
            UUID eventId,
            UUID ticketCategoryId
    ) {

        StandardResponse<TicketCategoryResponse> response =
                eventsRestClient
                        .get()
                        .uri(
                                "/api/v1/events/{eventId}/ticket-categories/{ticketCategoryId}",
                                eventId,
                                ticketCategoryId
                        )
                        .header(
                                "Authorization",
                                "Bearer " + serviceTokenProvider.getToken()
                        )
                        .retrieve()
                        .body(
                                new ParameterizedTypeReference<
                                        StandardResponse<TicketCategoryResponse>
                                        >() {
                                }
                        );

        if (response == null || response.data() == null) {
            throw new IllegalStateException(
                    "ms-events no devolvió una categoría de ticket válida."
            );
        }

        return response.data();
    }
}
