package com.railbound.portal.releases;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentReleaseRepository extends MongoRepository<ContentRelease, String> {

    Optional<ContentRelease> findByReleaseId(String releaseId);

    List<ContentRelease> findAllByOrderByCreatedAtDesc();

    List<ContentRelease> findByStatusOrderByPublishedAtDesc(String status);

    Optional<ContentRelease> findTopByStatusOrderByPublishedAtDesc(String status);
}
