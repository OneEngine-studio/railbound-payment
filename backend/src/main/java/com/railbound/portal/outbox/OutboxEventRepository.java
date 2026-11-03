package com.railbound.portal.outbox;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends MongoRepository<OutboxEvent, String> {

    Optional<OutboxEvent> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<OutboxEvent> findTop50ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
            Collection<String> statuses, Instant now);

    long countByStatus(String status);
}
