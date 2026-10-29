package com.railbound.portal.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class MockPaymentAdapter implements PaymentGatewayAdapter {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentAdapter.class);

    private final String secret;
    private final String checkoutBaseUrl;

    public MockPaymentAdapter(
            @Value("${app.payment.mock.secret:mock_webhook_secret_key_99182}") String secret,
            @Value("${app.payment.mock.checkout-base-url:http://localhost:3000/checkout/hosted-mock}") String checkoutBaseUrl) {
        this.secret = secret;
        this.checkoutBaseUrl = checkoutBaseUrl;
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(PaymentOrder order, String returnUrl) {
        String sessionId = "sess_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String checkoutUrl = checkoutBaseUrl + "?orderId=" + order.getOrderId() + "&sessionId=" + sessionId;
        return new CheckoutSessionResult(sessionId, checkoutUrl);
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        if (signatureHeader == null || rawBody == null) {
            return false;
        }

        try {
            String computed = computeHmacSha256(rawBody, secret);
            return computed.equalsIgnoreCase(signatureHeader.trim());
        } catch (Exception e) {
            log.error("Error computing signature verification", e);
            return false;
        }
    }

    public String computeHmacSha256(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    public String getSecret() {
        return secret;
    }
}
