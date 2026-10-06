package cl.eventpass.ms_tickets.document;

import cl.eventpass.ms_tickets.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "tickets")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
@CompoundIndex(
        name = "user_event_idx",
        def = "{'user_id':1, 'event_id': 1}"
)
@CompoundIndex(
        name = "order_item_ticket_idx",
        def = "{'order_item_id': 1, 'ticket_index': 1}",
        unique = true
)
public class TicketDocument extends BaseDocument {

    @Field("order_id")
    private UUID orderId;

    @Field("order_item_id")
    private UUID orderItemId;

    @Field("ticket_index")
    private Integer ticketIndex;

    @Field("user_id")
    private UUID userId;

    @Field("event_id")
    private UUID eventId;

    @Field("ticket_category_id")
    private UUID ticketCategoryId;

    @Indexed(unique = true)
    @Field("ticket_code")
    private String ticketCode;

    @Field("status")
    private TicketStatus status;

    @Field("used_at")
    private Instant usedAt;
}
