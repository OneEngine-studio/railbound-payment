package com.railbound.portal.shop;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "shop_purchases")
@CompoundIndexes({
        @CompoundIndex(name = "idx_user_purchase_idempotency", def = "{'userId': 1, 'idempotencyKey': 1}", unique = true),
        @CompoundIndex(name = "idx_user_purchases_created", def = "{'userId': 1, 'createdAt': -1}")
})
public class ShopPurchase {

    @Id
    private String id;

    @Indexed(unique = true)
    private String purchaseId;

    private String userId;
    private String itemSku;
    private long priceSnapshot;
    private String status; // COMPLETED, REFUNDED
    private String idempotencyKey;
    private Instant createdAt;

    public ShopPurchase() {
        this.createdAt = Instant.now();
        this.status = "COMPLETED";
    }

    public ShopPurchase(String purchaseId, String userId, String itemSku, long priceSnapshot, String idempotencyKey) {
        this.purchaseId = purchaseId;
        this.userId = userId;
        this.itemSku = itemSku;
        this.priceSnapshot = priceSnapshot;
        this.idempotencyKey = idempotencyKey;
        this.status = "COMPLETED";
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPurchaseId() {
        return purchaseId;
    }

    public void setPurchaseId(String purchaseId) {
        this.purchaseId = purchaseId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getItemSku() {
        return itemSku;
    }

    public void setItemSku(String itemSku) {
        this.itemSku = itemSku;
    }

    public long getPriceSnapshot() {
        return priceSnapshot;
    }

    public void setPriceSnapshot(long priceSnapshot) {
        this.priceSnapshot = priceSnapshot;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
