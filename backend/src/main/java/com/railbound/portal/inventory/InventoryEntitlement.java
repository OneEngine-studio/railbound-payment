package com.railbound.portal.inventory;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "inventory_entitlements")
@CompoundIndexes({
        @CompoundIndex(name = "idx_user_item_entitlement_unique", def = "{'userId': 1, 'itemSku': 1}", unique = true),
        @CompoundIndex(name = "idx_user_entitlements_state", def = "{'userId': 1, 'state': 1}")
})
public class InventoryEntitlement {

    @Id
    private String id;

    @Indexed(unique = true)
    private String entitlementId;

    private String userId;
    private String itemSku;
    private Instant acquiredAt;
    private String sourcePurchaseId;
    private String state; // ACTIVE, REVOKED
    private String equippedSlot; // e.g. OPTIC, BARREL, OPERATOR (null if not equipped)
    private String equippedWeaponSku;

    public InventoryEntitlement() {
        this.acquiredAt = Instant.now();
        this.state = "ACTIVE";
    }

    public InventoryEntitlement(String entitlementId, String userId, String itemSku, String sourcePurchaseId) {
        this.entitlementId = entitlementId;
        this.userId = userId;
        this.itemSku = itemSku;
        this.sourcePurchaseId = sourcePurchaseId;
        this.state = "ACTIVE";
        this.acquiredAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEntitlementId() {
        return entitlementId;
    }

    public void setEntitlementId(String entitlementId) {
        this.entitlementId = entitlementId;
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

    public Instant getAcquiredAt() {
        return acquiredAt;
    }

    public void setAcquiredAt(Instant acquiredAt) {
        this.acquiredAt = acquiredAt;
    }

    public String getSourcePurchaseId() {
        return sourcePurchaseId;
    }

    public void setSourcePurchaseId(String sourcePurchaseId) {
        this.sourcePurchaseId = sourcePurchaseId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getEquippedSlot() {
        return equippedSlot;
    }

    public void setEquippedSlot(String equippedSlot) {
        this.equippedSlot = equippedSlot;
    }

    public String getEquippedWeaponSku() {
        return equippedWeaponSku;
    }

    public void setEquippedWeaponSku(String equippedWeaponSku) {
        this.equippedWeaponSku = equippedWeaponSku;
    }
}
