package com.railbound.portal.refunds;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Auditable record of a fiat refund request and the resulting Real Coin
 * reversal decision.
 *
 * <p>Provider refunds and coin reversals are deliberately separate steps: the
 * gateway may accept a refund while the player has already spent the coins. In
 * that case the case becomes {@code REVIEW_REQUIRED} instead of creating a
 * negative wallet balance.</p>
 */
@Document(collection = "refund_cases")
@CompoundIndexes({
        @CompoundIndex(name = "idx_refund_order_created", def = "{'orderId': 1, 'createdAt': -1}")
})
public class RefundCase {

    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_PROVIDER_REFUNDED = "PROVIDER_REFUNDED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    private String id;

    @Indexed(unique = true)
    private String refundCaseId;

    /** One full-refund case per order; partial refunds are out of MVP scope. */
    @Indexed(unique = true)
    private String orderId;

    private String userId;
    private String provider;
    private String providerPaymentId;
    private String providerRefundId;
    private long amountMinor;
    private String currency;
    private long coinsToReverse;
    private long coinsReversed;
    private String status;
    private String reason;
    private String providerMessage;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public RefundCase() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRefundCaseId() {
        return refundCaseId;
    }

    public void setRefundCaseId(String refundCaseId) {
        this.refundCaseId = refundCaseId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public void setProviderPaymentId(String providerPaymentId) {
        this.providerPaymentId = providerPaymentId;
    }

    public String getProviderRefundId() {
        return providerRefundId;
    }

    public void setProviderRefundId(String providerRefundId) {
        this.providerRefundId = providerRefundId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public long getCoinsToReverse() {
        return coinsToReverse;
    }

    public void setCoinsToReverse(long coinsToReverse) {
        this.coinsToReverse = coinsToReverse;
    }

    public long getCoinsReversed() {
        return coinsReversed;
    }

    public void setCoinsReversed(long coinsReversed) {
        this.coinsReversed = coinsReversed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getProviderMessage() {
        return providerMessage;
    }

    public void setProviderMessage(String providerMessage) {
        this.providerMessage = providerMessage;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
