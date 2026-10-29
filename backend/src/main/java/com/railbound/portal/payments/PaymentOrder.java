package com.railbound.portal.payments;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "payment_orders")
@CompoundIndexes({
        @CompoundIndex(name = "idx_orders_user_created", def = "{'userId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_orders_status_created", def = "{'status': 1, 'createdAt': -1}")
})
public class PaymentOrder {

    @Id
    private String id;

    @Indexed(unique = true)
    private String orderId;

    private String userId;
    private String bundleSku;

    // Immutable price & coin snapshot from trusted configuration
    private long coinsToCredit;
    private long amountMinor;
    private String currency;

    private String provider; // "mock", "stripe", "xsolla"

    @Indexed(sparse = true)
    private String providerSessionId;

    private PaymentOrderStatus status;
    private String idempotencyKey;

    private Instant createdAt;
    private Instant expiresAt;
    private Instant completedAt;

    public PaymentOrder() {
        this.createdAt = Instant.now();
        this.status = PaymentOrderStatus.CREATED;
    }

    public PaymentOrder(String orderId, String userId, String bundleSku, long coinsToCredit,
                        long amountMinor, String currency, String provider, String idempotencyKey, Instant expiresAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.bundleSku = bundleSku;
        this.coinsToCredit = coinsToCredit;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.provider = provider;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentOrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getBundleSku() {
        return bundleSku;
    }

    public void setBundleSku(String bundleSku) {
        this.bundleSku = bundleSku;
    }

    public long getCoinsToCredit() {
        return coinsToCredit;
    }

    public void setCoinsToCredit(long coinsToCredit) {
        this.coinsToCredit = coinsToCredit;
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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderSessionId() {
        return providerSessionId;
    }

    public void setProviderSessionId(String providerSessionId) {
        this.providerSessionId = providerSessionId;
    }

    public PaymentOrderStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentOrderStatus status) {
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

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
