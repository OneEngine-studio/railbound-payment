package com.railbound.portal.assets;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Short-lived, single-use upload grant.
 *
 * <p>It replaces a cloud presigned URL: the token encodes the destination key,
 * the allowed content type and the maximum size, and is consumed on first use.
 * The token itself is opaque and never reveals the storage layout.</p>
 */
@Document(collection = "upload_tokens")
public class UploadToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String token;

    private String objectKey;
    private String contentType;
    private long maxBytes;

    private String createdBy;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant createdAt;

    public UploadToken() {
        this.createdAt = Instant.now();
    }

    public UploadToken(String token, String objectKey, String contentType,
                       long maxBytes, String createdBy, Instant expiresAt) {
        this();
        this.token = token;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.maxBytes = maxBytes;
        this.createdBy = createdBy;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt != null && now.isBefore(expiresAt);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
