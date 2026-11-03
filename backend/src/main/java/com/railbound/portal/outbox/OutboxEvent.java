package com.railbound.portal.outbox;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Transactional outbox row.
 *
 * <p>Business writes (wallet credit, purchase, release publish) insert an outbox
 * row in the same MongoDB transaction, so an event can never be lost or emitted
 * for a rolled-back change. A background relay then delivers it to notifications
 * and, when configured, to Nakama. Delivery is at-least-once; consumers must
 * deduplicate on {@code eventId}.</p>
 */
@Document(collection = "outbox_events")
@CompoundIndexes({
        @CompoundIndex(name = "idx_status_next_attempt", def = "{'status': 1, 'nextAttemptAt': 1}")
})
public class OutboxEvent {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD = "DEAD";

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId;

    /** Fully qualified event type, e.g. {@code WALLET_CREDITED}. */
    @Indexed
    private String type;

    private String aggregateType;
    private String aggregateId;

    /** Sanitized event body; never contains provider secrets. */
    private Map<String, Object> payload;

    @Indexed
    private String status;

    private int attempts;
    private Instant nextAttemptAt;
    private Instant createdAt;
    private Instant sentAt;
    private String lastError;

    public OutboxEvent() {
        this.status = STATUS_PENDING;
        this.attempts = 0;
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
    }

    public OutboxEvent(String eventId, String type, String aggregateType,
                       String aggregateId, Map<String, Object> payload) {
        this();
        this.eventId = eventId;
        this.type = type;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
