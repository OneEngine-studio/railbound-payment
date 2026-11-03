package com.railbound.portal.payments;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends MongoRepository<PaymentOrder, String> {
    Optional<PaymentOrder> findByOrderId(String orderId);
    Optional<PaymentOrder> findByProviderSessionId(String providerSessionId);
    Optional<PaymentOrder> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
    List<PaymentOrder> findByUserIdOrderByCreatedAtDesc(String userId);
    List<PaymentOrder> findAllByOrderByCreatedAtDesc();

    List<PaymentOrder> findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            PaymentOrderStatus status, Instant cutoff);
}
