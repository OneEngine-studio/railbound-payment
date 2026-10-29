package com.railbound.portal.inventory;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryEntitlementRepository extends MongoRepository<InventoryEntitlement, String> {
    List<InventoryEntitlement> findByUserIdAndState(String userId, String state);
    Optional<InventoryEntitlement> findByUserIdAndItemSku(String userId, String itemSku);
    boolean existsByUserIdAndItemSkuAndState(String userId, String itemSku, String state);
    Optional<InventoryEntitlement> findByUserIdAndEquippedSlot(String userId, String equippedSlot);
}
