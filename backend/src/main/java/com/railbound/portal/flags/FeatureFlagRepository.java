package com.railbound.portal.flags;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureFlagRepository extends MongoRepository<FeatureFlag, String> {

    Optional<FeatureFlag> findByFlag(String flag);

    List<FeatureFlag> findAllByOrderByFlagAsc();
}
