package com.railbound.portal.refunds;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundCaseRepository extends MongoRepository<RefundCase, String> {
    Optional<RefundCase> findByRefundId(String refundId);
    Optional<RefundCase> findByOrderId(String orderId);
    List<RefundCase> findAllByOrderByCreatedAtDesc();
}
