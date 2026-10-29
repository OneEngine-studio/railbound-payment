package com.railbound.portal.shop;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopPurchaseRepository extends MongoRepository<ShopPurchase, String> {
    Optional<ShopPurchase> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
    List<ShopPurchase> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<ShopPurchase> findByPurchaseId(String purchaseId);
}
