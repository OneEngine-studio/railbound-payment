package com.railbound.portal.payments;

/**
 * Provider-neutral, signature-verified payment notification.
 *
 * <p>Only the fields needed to make an authoritative fulfillment decision are
 * kept. Raw card data (PayHere may include a masked card number) is never
 * carried into the domain model.</p>
 *
 * @param providerEventId unique provider event identifier used for deduplication
 * @param orderId         merchant order id that was originally sent to the gateway
 * @param providerPaymentId gateway payment reference (PayHere payment_id)
 * @param outcome         normalized outcome
 * @param amountMinor     amount in minor currency units, when supplied
 * @param currency        ISO currency code, when supplied
 * @param statusCode      raw provider status code (kept for audit/debugging)
 * @param sanitizedPayload minimal JSON summary safe to persist
 */
public record PaymentNotification(
        String providerEventId,
        String orderId,
        String providerPaymentId,
        Outcome outcome,
        Long amountMinor,
        String currency,
        String statusCode,
        String sanitizedPayload
) {

    public enum Outcome {
        /** Money was captured; wallet may be credited exactly once. */
        PAID,
        /** Provider accepted the request but has not captured money yet. */
        PENDING,
        /** Customer cancelled the hosted page. */
        CANCELLED,
        /** Provider declined or the payment failed. */
        FAILED,
        /** Cardholder/issuer chargeback; requires reviewed reversal. */
        CHARGEDBACK,
        /** Provider reports the payment as refunded. */
        REFUNDED
    }
}
