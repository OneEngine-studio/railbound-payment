package com.railbound.portal.payments;

import java.util.Map;

/**
 * Provider-agnostic boundary for hosted checkout + server callback handling.
 *
 * <p>Business rules (wallet credit, ledger, idempotency) deliberately live in
 * {@link PaymentService}; an adapter must never mutate wallets. This keeps the
 * wallet logic identical no matter which gateway is used.</p>
 *
 * <p>Security rule: adapters that accept callbacks must verify the provider's
 * own signature scheme on the raw request before returning a
 * {@link PaymentNotification}. A client-reported "success" must never become a
 * notification.</p>
 */
public interface PaymentGatewayAdapter {

    /** Stable lowercase provider identifier used in URLs and persisted orders. */
    String getProviderName();

    /**
     * True when the provider is enabled and has the minimum credentials needed to
     * create a checkout session. A disabled/unconfigured provider must fail loudly
     * instead of silently falling back to a mock.
     */
    boolean isConfigured();

    CheckoutSessionResult createCheckoutSession(PaymentOrder order, CheckoutContext context);

    /**
     * Verifies the provider signature and normalizes the callback.
     *
     * @throws com.railbound.portal.common.InvalidSignatureException when the
     *         callback cannot be cryptographically authenticated.
     */
    PaymentNotification verifyAndParseNotification(WebhookRequest request);

    /**
     * Context needed by real gateways. Customer fields are only used to pre-fill
     * the gateway's hosted page; they are never persisted with payment records.
     */
    record CheckoutContext(
            String returnUrl,
            String cancelUrl,
            String notifyUrl,
            String customerFirstName,
            String customerLastName,
            String customerEmail,
            String customerPhone,
            String customerAddress,
            String customerCity,
            String customerCountry
    ) {}

    /**
     * @param providerSessionId provider-side session/payment reference when one exists
     * @param checkoutUrl       direct hosted URL, or the gateway form action URL
     * @param formFields        non-empty when the gateway requires an HTML form POST
     */
    record CheckoutSessionResult(
            String providerSessionId,
            String checkoutUrl,
            Map<String, String> formFields
    ) {
        public boolean requiresFormPost() {
            return formFields != null && !formFields.isEmpty();
        }
    }

    /**
     * Raw callback data. PayHere sends form-encoded data, while other providers
     * may send JSON; adapters decide how to parse and verify.
     */
    record WebhookRequest(
            String rawBody,
            Map<String, String> headers,
            Map<String, String> formParams
    ) {
        public String header(String name) {
            if (headers == null || name == null) {
                return null;
            }
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (name.equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue();
                }
            }
            return null;
        }

        public String param(String name) {
            return formParams == null || name == null ? null : formParams.get(name);
        }
    }
}
