package com.railbound.portal.releases;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Immutable record of one buildable content version for a catalog item.
 *
 * <p>Publishing metadata alone cannot make Unity load an asset: the release
 * pipeline requires a concrete bundle key, SHA-256 checksum and size before the
 * item may be advertised as playable.</p>
 */
@Document(collection = "item_versions")
@CompoundIndexes({
        @CompoundIndex(name = "idx_sku_version", def = "{'itemSku': 1, 'version': 1}", unique = true),
        @CompoundIndex(name = "idx_sku_validation", def = "{'itemSku': 1, 'validationStatus': 1, 'createdAt': -1}")
})
public class ItemVersion {

    public static final String VALIDATION_VALID = "VALID";
    public static final String VALIDATION_INVALID = "INVALID";

    @Id
    private String id;

    @Indexed
    private String itemSku;

    private String version;
    private String minimumClientVersion;

    /** Object-storage key, e.g. {@code bundles/weapons/acog_1.0.1.bundle}. */
    private String bundleKey;

    /** Lower-case hex SHA-256 of the exact bundle bytes. */
    private String sha256;

    private long sizeBytes;

    private String validationStatus;
    private String validationMessage;

    private String createdBy;
    private Instant createdAt;

    public ItemVersion() {
        this.createdAt = Instant.now();
        this.validationStatus = VALIDATION_INVALID;
    }

    public ItemVersion(String itemSku, String version, String minimumClientVersion,
                       String bundleKey, String sha256, long sizeBytes, String createdBy) {
        this();
        this.itemSku = itemSku;
        this.version = version;
        this.minimumClientVersion = minimumClientVersion;
        this.bundleKey = bundleKey;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.createdBy = createdBy;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getMinimumClientVersion() {
        return minimumClientVersion;
    }

    public void setMinimumClientVersion(String minimumClientVersion) {
        this.minimumClientVersion = minimumClientVersion;
    }

    public String getBundleKey() {
        return bundleKey;
    }

    public void setBundleKey(String bundleKey) {
        this.bundleKey = bundleKey;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    public String getValidationMessage() {
        return validationMessage;
    }

    public void setValidationMessage(String validationMessage) {
        this.validationMessage = validationMessage;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
