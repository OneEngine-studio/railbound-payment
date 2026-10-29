package com.railbound.portal.announcements;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnnouncementRepository extends MongoRepository<Announcement, String> {
    List<Announcement> findByStatusOrderByPublishAtDesc(String status);
    Optional<Announcement> findByAnnouncementId(String announcementId);
    List<Announcement> findAllByOrderByPublishAtDesc();
}
