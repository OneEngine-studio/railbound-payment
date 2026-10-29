package com.railbound.portal.shop;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopItemRepository extends MongoRepository<ShopItem, String> {
    List<ShopItem> findByPublicationStatus(PublicationStatus status);
    List<ShopItem> findByPublicationStatusAndCategory(PublicationStatus status, ItemCategory category);
    Optional<ShopItem> findBySku(String sku);
}
