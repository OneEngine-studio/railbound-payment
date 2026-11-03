package com.railbound.portal.notifications;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Player-facing notification. Delivered to the web portal and to Unity through
 * {@code GET /game/sync}; created by the transactional outbox so a notification
 * can never exist without the business event that produced it.
 */
@Document(collection = "notifications")
@CompoundIndexes({
        @CompoundIndex(name = "idx_user_created", def = "{'userId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_user_unread", def = "{'userId': 1, 'readAt': 1}")
})
public class Notification {

    @Id
    private String id;

    @Indexed(unique = true)
    private String notificationId;

    @Indexed
    private String userId;

    private String type;
    private String title;
    private String body;

    /** Small, sanitized event payload for client rendering. */
    private Map<String, Object> payload;

    private Instant readAt;
    private Instant createdAt;

    public Notification() {
        this.createdAt = Instant.now();
    }

    public Notification(String notificationId, String userId, String type,
                        String title, String body, Map<String, Object> payload) {
        this();
        this.notificationId = notificationId;
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.payload = payload;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
