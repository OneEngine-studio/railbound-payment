package com.railbound.portal.ledger;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "wallet_ledger")
@CompoundIndexes({
        @CompoundIndex(name = "idx_user_created", def = "{'userId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_ref_unique", def = "{'referenceType': 1, 'referenceId': 1}", unique = true)
})
public class WalletLedgerEntry {

    @Id
    private String id;

    @Indexed(unique = true)
    private String entryId;

    private String userId;
    private LedgerEntryType type;
    private long delta;
    private long balanceAfter;
    private String referenceType;
    private String referenceId;
    private String description;
    private Instant createdAt;

    public WalletLedgerEntry() {
        this.createdAt = Instant.now();
    }

    public WalletLedgerEntry(String entryId, String userId, LedgerEntryType type, long delta,
                             long balanceAfter, String referenceType, String referenceId, String description) {
        this.entryId = entryId;
        this.userId = userId;
        this.type = type;
        this.delta = delta;
        this.balanceAfter = balanceAfter;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEntryId() {
        return entryId;
    }

    public void setEntryId(String entryId) {
        this.entryId = entryId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LedgerEntryType getType() {
        return type;
    }

    public void setType(LedgerEntryType type) {
        this.type = type;
    }

    public long getDelta() {
        return delta;
    }

    public void setDelta(long delta) {
        this.delta = delta;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(long balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
