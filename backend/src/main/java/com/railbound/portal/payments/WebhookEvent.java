package com.railbound.portal.payments;

/**
 * Provider-neutral view of a payment notification AFTER its signature has been verified.
 * PaymentService only ever works with this type, so wallet/ledger logic never depends on a
 * particular provider's wire format.
 *
 * @param eventId           stable de-duplication key (unique per provider)
 * @param orderId           our order id as echoed back by the provider
 * @param outcome           normalized result
 * @param amountMinor       amount the provider says was paid, in minor units (null if unparseable)
 * @param currency          currency the provider says was paid
 * @param providerPaymentId provider's own payment reference (may be null)
 * @param statusMessage     human readable provider message (may be null)
 * @param sanitizedPayload  payload with card/PII fields removed, safe to persist
 */
public record WebhookEvent(
        String eventId,
        String orderId,
        Outcome outcome,
        Long amountMinor,
        String currency,
        String providerPaymentId,
        String statusMessage,
        String sanitizedPayload) {

    public enum Outcome {
        SUCCEEDED,
        PENDING,
        CANCELLED,
        FAILED,
        CHARGED_BACK
    }
}
