package com.railbound.portal.releases;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Approved, publishable bundle of item versions.
 *
 * <p>Only a {@code PUBLISHED} release is served to game clients. Publishing is a
 * deliberate two-step approval (approve then publish) so a content manager
 * cannot push client-breaking content alone.</p>
 */
@Document(collection = "content_releases")
public class ContentRelease {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    public static final String STATUS_ROLLED_BACK = "ROLLED_BACK";

    @Id
    private String id;

    @Indexed(unique = true)
    private String releaseId;

    private String version;
    private String manifestVersion;

    private List<ReleaseItemRef> items = new ArrayList<>();
    private List<String> targetClientVersions = new ArrayList<>();

    private String status;
    private String notes;

    private String createdBy;
    private String approvedBy;
    private Instant approvedAt;
    private Instant publishedAt;
    private Instant createdAt;

    public ContentRelease() {
        this.createdAt = Instant.now();
        this.status = STATUS_DRAFT;
        this.manifestVersion = "1.0.0";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(String releaseId) {
        this.releaseId = releaseId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getManifestVersion() {
        return manifestVersion;
    }

    public void setManifestVersion(String manifestVersion) {
        this.manifestVersion = manifestVersion;
    }

    public List<ReleaseItemRef> getItems() {
        return items;
    }

    public void setItems(List<ReleaseItemRef> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }

    public List<String> getTargetClientVersions() {
        return targetClientVersions;
    }

    public void setTargetClientVersions(List<String> targetClientVersions) {
        this.targetClientVersions = targetClientVersions == null ? new ArrayList<>() : targetClientVersions;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /** Reference to the exact item version included in this release. */
    public static class ReleaseItemRef {
        private String itemSku;
        private String version;

        public ReleaseItemRef() {
        }

        public ReleaseItemRef(String itemSku, String version) {
            this.itemSku = itemSku;
            this.version = version;
        }

        public String getItemSku() {
            return itemSku;
        }

        public void setItemSku(String itemSku) {
            this.itemSku = itemSku;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }
}
