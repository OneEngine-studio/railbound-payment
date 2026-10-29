package com.railbound.portal.shop;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "shop_items")
@CompoundIndexes({
        @CompoundIndex(name = "idx_status_category", def = "{'publicationStatus': 1, 'category': 1}")
})
public class ShopItem {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sku;

    private ItemCategory category;
    private String title;
    private String description;
    private long realCoinPrice;

    private PublicationStatus publicationStatus;
    private Map<String, Object> compatibility;
    private Map<String, String> previewMedia;
    private String currentContentVersion;

    private Instant createdAt;
    private Instant updatedAt;

    public ShopItem() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.publicationStatus = PublicationStatus.DRAFT;
    }

    public ShopItem(String sku, ItemCategory category, String title, String description,
                    long realCoinPrice, PublicationStatus publicationStatus,
                    Map<String, Object> compatibility, Map<String, String> previewMedia,
                    String currentContentVersion) {
        this.sku = sku;
        this.category = category;
        this.title = title;
        this.description = description;
        this.realCoinPrice = realCoinPrice;
        this.publicationStatus = publicationStatus;
        this.compatibility = compatibility;
        this.previewMedia = previewMedia;
        this.currentContentVersion = currentContentVersion;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public ItemCategory getCategory() {
        return category;
    }

    public void setCategory(ItemCategory category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getRealCoinPrice() {
        return realCoinPrice;
    }

    public void setRealCoinPrice(long realCoinPrice) {
        this.realCoinPrice = realCoinPrice;
    }

    public PublicationStatus getPublicationStatus() {
        return publicationStatus;
    }

    public void setPublicationStatus(PublicationStatus publicationStatus) {
        this.publicationStatus = publicationStatus;
    }

    public Map<String, Object> getCompatibility() {
        return compatibility;
    }

    public void setCompatibility(Map<String, Object> compatibility) {
        this.compatibility = compatibility;
    }

    public Map<String, String> getPreviewMedia() {
        return previewMedia;
    }

    public void setPreviewMedia(Map<String, String> previewMedia) {
        this.previewMedia = previewMedia;
    }

    public String getCurrentContentVersion() {
        return currentContentVersion;
    }

    public void setCurrentContentVersion(String currentContentVersion) {
        this.currentContentVersion = currentContentVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
