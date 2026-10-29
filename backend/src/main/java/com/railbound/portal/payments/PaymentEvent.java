package com.railbound.portal.payments;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "payment_events")
@CompoundIndexes({
        @CompoundIndex(name = "idx_provider_event_unique", def = "{'provider': 1, 'providerEventId': 1}", unique = true)
})
public class PaymentEvent {

    @Id
    private String id;

    private String provider;
    private String providerEventId;
    private String orderId;
    private Instant receivedAt;
    private Instant verifiedAt;
    private String processingStatus; // PROCESSED, DUPLICATE_IGNORED, FAILED
    private String sanitizedPayload;

    public PaymentEvent() {
        this.receivedAt = Instant.now();
    }

    public PaymentEvent(String provider, String providerEventId, String orderId, String processingStatus, String sanitizedPayload) {
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.orderId = orderId;
        this.processingStatus = processingStatus;
        this.sanitizedPayload = sanitizedPayload;
        this.receivedAt = Instant.now();
        this.verifiedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public void setProviderEventId(String providerEventId) {
        this.providerEventId = providerEventId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getSanitizedPayload() {
        return sanitizedPayload;
    }

    public void setSanitizedPayload(String sanitizedPayload) {
        this.sanitizedPayload = sanitizedPayload;
    }
}
