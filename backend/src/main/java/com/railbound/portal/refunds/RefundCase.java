package com.railbound.portal.refunds;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** One refund or chargeback against one completed top-up order (at most one case per order). */
@Document(collection = "refund_cases")
public class RefundCase {

    @Id
    private String id;

    @Indexed(unique = true)
    private String refundId;

    @Indexed(unique = true)
    private String orderId;

    private String userId;
    private RefundType type;
    private RefundStatus status;

    private long amountMinor;
    private String currency;
    private long coinsToReverse;
    private long coinsReversed;

    private String reason;
    private String providerRefundId;
    private String resolutionNote;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public RefundCase() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getRefundId() { return refundId; }
    public void setRefundId(String refundId) { this.refundId = refundId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public RefundType getType() { return type; }
    public void setType(RefundType type) { this.type = type; }
    public RefundStatus getStatus() { return status; }
    public void setStatus(RefundStatus status) { this.status = status; }
    public long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(long amountMinor) { this.amountMinor = amountMinor; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public long getCoinsToReverse() { return coinsToReverse; }
    public void setCoinsToReverse(long coinsToReverse) { this.coinsToReverse = coinsToReverse; }
    public long getCoinsReversed() { return coinsReversed; }
    public void setCoinsReversed(long coinsReversed) { this.coinsReversed = coinsReversed; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getProviderRefundId() { return providerRefundId; }
    public void setProviderRefundId(String providerRefundId) { this.providerRefundId = providerRefundId; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
