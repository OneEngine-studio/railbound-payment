package com.railbound.portal.payments;

/**
 * Boundary between our wallet/order logic and a concrete payment provider. Adding a provider means
 * adding one implementation of this interface plus a line in PaymentService#getAdapter; nothing in
 * the wallet or ledger code changes.
 */
public interface PaymentGatewayAdapter {

    /** Lower-case provider id stored on orders, e.g. "payhere" or "mock". */
    String getProviderName();

    /** False when the provider is switched off or not fully configured. */
    boolean isEnabled();

    /**
     * Prepares provider-side state for a new checkout.
     *
     * @param order         the persisted order (amount/currency snapshot is already final)
     * @param checkoutToken one-time bearer secret that authorizes opening the hosted checkout page
     */
    CheckoutSessionResult createCheckoutSession(PaymentOrder order, String checkoutToken);

    /**
     * Verifies the provider's signature over the exact raw request body and converts it to a
     * provider-neutral event.
     *
     * @throws com.railbound.portal.common.InvalidSignatureException when the notification is not
     *         genuinely from the provider
     */
    WebhookEvent verifyAndParseWebhook(String rawBody, String signatureHeader);

    record CheckoutSessionResult(String providerSessionId, String checkoutUrl) {}
}
