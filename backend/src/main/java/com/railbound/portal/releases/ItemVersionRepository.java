package com.railbound.portal.releases;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemVersionRepository extends MongoRepository<ItemVersion, String> {

    List<ItemVersion> findByItemSkuOrderByCreatedAtDesc(String itemSku);

    Optional<ItemVersion> findByItemSkuAndVersion(String itemSku, String version);

    boolean existsByItemSkuAndVersion(String itemSku, String version);

    Optional<ItemVersion> findTopByItemSkuAndValidationStatusOrderByCreatedAtDesc(
            String itemSku, String validationStatus);
}
