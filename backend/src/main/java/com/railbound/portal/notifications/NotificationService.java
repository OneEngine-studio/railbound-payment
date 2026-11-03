package com.railbound.portal.notifications;

import com.railbound.portal.common.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a notification idempotently: the outbox may retry an event, so the
     * derived notification id must be stable and unique-indexed.
     */
    public Notification create(String notificationId, String userId, String type,
                               String title, String body, Map<String, Object> payload) {
        if (notificationId == null || notificationId.isBlank()) {
            notificationId = "ntf_" + UUID.randomUUID().toString().replace("-", "");
        }
        if (repository.existsByNotificationId(notificationId)) {
            log.debug("Notification {} already projected; skipping", notificationId);
            return null;
        }
        return repository.save(new Notification(notificationId, userId, type, title, body, payload));
    }

    public List<Notification> getMyNotifications(String userId) {
        return repository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(String userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    public Notification markRead(String userId, String notificationId) {
        Notification notification = repository.findByUserIdAndNotificationId(userId, notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification = repository.save(notification);
        }
        return notification;
    }

    public int markAllRead(String userId) {
        List<Notification> unread = repository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> n.getReadAt() == null)
                .toList();
        Instant now = Instant.now();
        unread.forEach(n -> n.setReadAt(now));
        repository.saveAll(unread);
        return unread.size();
    }
}
