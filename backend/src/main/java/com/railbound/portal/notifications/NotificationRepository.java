package com.railbound.portal.notifications;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Notification> findByUserIdAndNotificationId(String userId, String notificationId);

    boolean existsByNotificationId(String notificationId);

    long countByUserIdAndReadAtIsNull(String userId);
}
