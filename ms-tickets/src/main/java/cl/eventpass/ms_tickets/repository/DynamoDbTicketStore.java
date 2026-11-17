package cl.eventpass.ms_tickets.repository;

import cl.eventpass.ms_tickets.document.TicketDocument;
import cl.eventpass.ms_tickets.enums.TicketStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("dynamodb")
public class DynamoDbTicketStore implements TicketStore {

    private final DynamoDbClient dynamo;
    private final String table;

    public DynamoDbTicketStore(DynamoDbClient dynamo, @Value("${app.tickets.dynamodb.table}") String table) {
        this.dynamo = dynamo;
        this.table = table;
    }

    @Override
    public void insertIfAbsent(TicketDocument ticket) {
        var issuanceKey = key("issue#" + ticket.getOrderItemId() + "#" + ticket.getTicketIndex());
        var item = Map.ofEntries(
                Map.entry("pk", text("ticket#" + ticket.getTicketCode())),
                Map.entry("id", text(UUID.randomUUID())),
                Map.entry("orderId", text(ticket.getOrderId())),
                Map.entry("orderItemId", text(ticket.getOrderItemId())),
                Map.entry("ticketIndex", AttributeValue.fromN(ticket.getTicketIndex().toString())),
                Map.entry("userId", text(ticket.getUserId())),
                Map.entry("eventId", text(ticket.getEventId())),
                Map.entry("ticketCategoryId", text(ticket.getTicketCategoryId())),
                Map.entry("ticketCode", text(ticket.getTicketCode())),
                Map.entry("status", text(TicketStatus.ACTIVE)),
                Map.entry("createdAt", text(Instant.now()))
        );
        try {
            dynamo.transactWriteItems(request ->
                    request.transactItems(insert(issuanceKey), insert(item)));
        } catch (TransactionCanceledException error) {
            // Confirmar la marca distingue una retransmisión de un fallo de escritura.
            if (!dynamo.getItem(request -> request.tableName(table).key(issuanceKey).consistentRead(true)).hasItem()) {
                throw error;
            }
        }
    }

    @Override
    public List<TicketDocument> findByUserIdAndDeletedAtIsNull(UUID userId) {
        return dynamo.queryPaginator(request -> request.tableName(table).indexName("by-user")
                        .keyConditionExpression("userId = :user").expressionAttributeValues(Map.of(":user", text(userId))))
                .items().stream().filter(item -> !item.containsKey("deletedAt")).map(this::ticket).toList();
    }

    @Override
    public Optional<TicketDocument> findByTicketCodeAndDeletedAtIsNull(String code) {
        var result = dynamo.getItem(request ->
                request.tableName(table).key(key("ticket#" + code)).consistentRead(true));
        return Optional.ofNullable(result.hasItem() && !result.item().containsKey("deletedAt")
                ? ticket(result.item()) : null);
    }

    @Override
    public TicketDocument markAsUsed(String code) {
        try {
            var result = dynamo.updateItem(request -> request.tableName(table).key(key("ticket#" + code))
                    .conditionExpression("#status = :active AND attribute_not_exists(deletedAt)")
                    .updateExpression("SET #status = :used, usedAt = :time")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(Map.of(":active", text(TicketStatus.ACTIVE),
                            ":used", text(TicketStatus.USED), ":time", text(Instant.now())))
                    .returnValues(ReturnValue.ALL_NEW));
            return ticket(result.attributes());
        } catch (ConditionalCheckFailedException error) {
            return null;
        }
    }

    private TransactWriteItem insert(Map<String, AttributeValue> item) {
        return TransactWriteItem.builder().put(Put.builder().tableName(table).item(item)
                .conditionExpression("attribute_not_exists(pk)").build()).build();
    }

    private static Map<String, AttributeValue> key(String pk) {
        return Map.of("pk", text(pk));
    }

    private static AttributeValue text(Object value) {
        return AttributeValue.fromS(value.toString());
    }

    private TicketDocument ticket(Map<String, AttributeValue> item) {
        return TicketDocument.builder()
                .id(UUID.fromString(item.get("id").s()))
                .orderId(UUID.fromString(item.get("orderId").s()))
                .orderItemId(UUID.fromString(item.get("orderItemId").s()))
                .ticketIndex(Integer.parseInt(item.get("ticketIndex").n()))
                .userId(UUID.fromString(item.get("userId").s()))
                .eventId(UUID.fromString(item.get("eventId").s()))
                .ticketCategoryId(UUID.fromString(item.get("ticketCategoryId").s()))
                .ticketCode(item.get("ticketCode").s())
                .status(TicketStatus.valueOf(item.get("status").s()))
                .createdAt(Instant.parse(item.get("createdAt").s()))
                .usedAt(item.containsKey("usedAt") ? Instant.parse(item.get("usedAt").s()) : null)
                .build();
    }
}
