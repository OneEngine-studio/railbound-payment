package com.railbound.portal.payments;

public interface PaymentGatewayAdapter {

    String getProviderName();

    CheckoutSessionResult createCheckoutSession(PaymentOrder order, String returnUrl);

    boolean verifyWebhookSignature(String rawBody, String signatureHeader);

    record CheckoutSessionResult(String providerSessionId, String checkoutUrl) {}
}
