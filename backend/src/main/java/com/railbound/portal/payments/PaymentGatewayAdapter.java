package com.railbound.portal.payments;

public interface PaymentGatewayAdapter {

    String getProviderName();

    CheckoutSessionResult createCheckoutSession(PaymentOrder order, String returnUrl);

    boolean verifyWebhookSignature(String rawBody, String signatureHeader);

    /** The provider's own event, reduced to what fulfilment needs; empty when the event should be ignored. */
    java.util.Optional<WebhookEvent> parseWebhook(com.fasterxml.jackson.databind.JsonNode payload);

    record CheckoutSessionResult(String providerSessionId, String checkoutUrl) {}

    /** amountMinor is null when the provider's event carries no amount to check. */
    record WebhookEvent(String eventId, String orderId, String providerSessionId, Long amountMinor, String currency) {}
}
