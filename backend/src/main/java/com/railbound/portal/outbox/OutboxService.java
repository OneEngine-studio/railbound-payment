package com.railbound.portal.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Enqueues domain events inside the caller's MongoDB transaction.
 *
 * <p>Call this from the same {@code @Transactional} / {@code TransactionTemplate}
 * block that mutates business state. If the transaction rolls back the event
 * disappears with it; if it commits the relay will eventually deliver it.</p>
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    public static final String WALLET_CREDITED = "WALLET_CREDITED";
    public static final String PURCHASE_COMPLETED = "PURCHASE_COMPLETED";
    public static final String REFUND_COMPLETED = "REFUND_COMPLETED";
    public static final String RELEASE_PUBLISHED = "RELEASE_PUBLISHED";
    public static final String ANNOUNCEMENT_PUBLISHED = "ANNOUNCEMENT_PUBLISHED";

    private final OutboxEventRepository repository;

    public OutboxService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    public OutboxEvent enqueue(String type, String aggregateType, String aggregateId,
                               Map<String, Object> payload) {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        OutboxEvent event = new OutboxEvent(
                eventId,
                type,
                aggregateType,
                aggregateId,
                payload == null ? Map.of() : payload
        );
        OutboxEvent saved = repository.save(event);
        log.debug("Enqueued outbox event {} ({}) for {}:{}",
                eventId, type, aggregateType, aggregateId);
        return saved;
    }

    /** Test/ops helper: returns the number of undelivered events. */
    public long countPending() {
        return repository.countByStatus(OutboxEvent.STATUS_PENDING)
                + repository.countByStatus(OutboxEvent.STATUS_FAILED);
    }

    public Instant now() {
        return Instant.now();
    }
}
