package com.railbound.portal.announcements;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "announcements")
@CompoundIndexes({
        @CompoundIndex(name = "idx_status_publishAt", def = "{'status': 1, 'publishAt': -1}")
})
public class Announcement {

    @Id
    private String id;

    @Indexed(unique = true)
    private String announcementId;

    private String title;
    private String body;
    private String imageKey;
    private String linkedItemSku;
    private String status; // DRAFT, PUBLISHED, ARCHIVED
    private Instant publishAt;
    private String audience;
    private String createdBy;

    public Announcement() {
        this.publishAt = Instant.now();
        this.status = "DRAFT";
        this.audience = "ALL";
    }

    public Announcement(String announcementId, String title, String body, String imageKey,
                        String linkedItemSku, String status, String createdBy) {
        this.announcementId = announcementId;
        this.title = title;
        this.body = body;
        this.imageKey = imageKey;
        this.linkedItemSku = linkedItemSku;
        this.status = status;
        this.createdBy = createdBy;
        this.publishAt = Instant.now();
        this.audience = "ALL";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAnnouncementId() {
        return announcementId;
    }

    public void setAnnouncementId(String announcementId) {
        this.announcementId = announcementId;
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

    public String getImageKey() {
        return imageKey;
    }

    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public String getLinkedItemSku() {
        return linkedItemSku;
    }

    public void setLinkedItemSku(String linkedItemSku) {
        this.linkedItemSku = linkedItemSku;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getPublishAt() {
        return publishAt;
    }

    public void setPublishAt(Instant publishAt) {
        this.publishAt = publishAt;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
