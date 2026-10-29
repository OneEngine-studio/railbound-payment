package com.railbound.portal.bundles;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoinBundleRepository extends MongoRepository<CoinBundle, String> {
    List<CoinBundle> findByActiveTrueOrderBySortOrderAsc();
    Optional<CoinBundle> findBySku(String sku);
}
