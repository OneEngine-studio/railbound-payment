package com.railbound.portal.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Background relay for the transactional outbox.
 *
 * <p>Runs on a fixed delay and processes a bounded batch, so a single slow
 * consumer cannot starve the HTTP thread pool or grow an unbounded in-memory
 * queue. Failed events are retried with exponential backoff and eventually
 * parked as {@code DEAD} for operator review rather than retried forever.</p>
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int MAX_ATTEMPTS = 5;

    private final OutboxEventRepository repository;
    private final OutboxDispatcher dispatcher;
    private final boolean enabled;

    public OutboxRelay(
            OutboxEventRepository repository,
            OutboxDispatcher dispatcher,
            @Value("${app.outbox.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.enabled = enabled;
    }

    @Scheduled(
            initialDelayString = "${app.outbox.initial-delay-ms:15000}",
            fixedDelayString = "${app.outbox.relay-interval-ms:5000}")
    public void scheduledRelay() {
        if (!enabled) {
            return;
        }
        try {
            relayOnce();
        } catch (Exception e) {
            // Never let a relay failure kill the scheduler thread.
            log.error("Outbox relay iteration failed", e);
        }
    }

    /**
     * Processes one batch and returns the number of events that were delivered.
     * Exposed for deterministic tests and for an operator-triggered drain.
     */
    public int relayOnce() {
        Instant now = Instant.now();
        List<OutboxEvent> batch = repository.findTop50ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                List.of(OutboxEvent.STATUS_PENDING, OutboxEvent.STATUS_FAILED), now);

        int delivered = 0;
        for (OutboxEvent event : batch) {
            try {
                dispatcher.dispatch(event);
                event.setStatus(OutboxEvent.STATUS_SENT);
                event.setSentAt(Instant.now());
                event.setLastError(null);
                repository.save(event);
                delivered++;
            } catch (Exception e) {
                int attempts = event.getAttempts() + 1;
                event.setAttempts(attempts);
                event.setLastError(truncate(e.getMessage()));
                if (attempts >= MAX_ATTEMPTS) {
                    event.setStatus(OutboxEvent.STATUS_DEAD);
                    log.error("Outbox event {} parked as DEAD after {} attempts: {}",
                            event.getEventId(), attempts, e.getMessage());
                } else {
                    event.setStatus(OutboxEvent.STATUS_FAILED);
                    event.setNextAttemptAt(Instant.now().plus(backoff(attempts)));
                    log.warn("Outbox event {} failed (attempt {}): {}",
                            event.getEventId(), attempts, e.getMessage());
                }
                repository.save(event);
            }
        }
        return delivered;
    }

    private Duration backoff(int attempts) {
        return switch (attempts) {
            case 1 -> Duration.ofSeconds(10);
            case 2 -> Duration.ofSeconds(30);
            case 3 -> Duration.ofMinutes(2);
            default -> Duration.ofMinutes(10);
        };
    }

    private static String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
