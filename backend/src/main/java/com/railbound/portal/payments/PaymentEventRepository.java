package com.railbound.portal.payments;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentEventRepository extends MongoRepository<PaymentEvent, String> {
    Optional<PaymentEvent> findByProviderAndProviderEventId(String provider, String providerEventId);
    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);
}
