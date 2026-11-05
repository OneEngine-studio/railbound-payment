package com.railbound.portal.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railbound.portal.common.InvalidSignatureException;
import com.railbound.portal.common.InvalidStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Development-only provider that simulates a hosted checkout. It signs its own webhooks with
 * HMAC-SHA256 and goes through exactly the same verification and fulfillment path as a real
 * provider. It is controlled by {@code app.payment.mock.enabled} and MUST be disabled in
 * production (see application-prod.yml).
 */
@Component
public class MockPaymentAdapter implements PaymentGatewayAdapter {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentAdapter.class);

    public static final String PROVIDER = "mock";

    private final String secret;
    private final String checkoutBaseUrl;
    private final boolean enabled;
    private final ObjectMapper objectMapper;

    public MockPaymentAdapter(
            @Value("${app.payment.mock.secret:mock_webhook_secret_key_99182}") String secret,
            @Value("${app.payment.mock.checkout-base-url:http://localhost:3000/checkout/hosted-mock}") String checkoutBaseUrl,
            @Value("${app.payment.mock.enabled:true}") boolean enabled,
            ObjectMapper objectMapper) {
        this.secret = secret;
        this.checkoutBaseUrl = checkoutBaseUrl;
        this.enabled = enabled;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return PROVIDER;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(PaymentOrder order, String checkoutToken) {
        String sessionId = "sess_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String checkoutUrl = checkoutBaseUrl + "?orderId=" + order.getOrderId() + "&sessionId=" + sessionId;
        return new CheckoutSessionResult(sessionId, checkoutUrl);
    }

    @Override
    public WebhookEvent verifyAndParseWebhook(String rawBody, String signatureHeader) {
        if (!verifyWebhookSignature(rawBody, signatureHeader)) {
            throw new InvalidSignatureException("Webhook signature verification failed");
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (IOException e) {
            throw new InvalidStateException("Malformed webhook payload");
        }

        String orderId = text(payload, "orderId");
        if (orderId == null || orderId.isBlank()) {
            throw new InvalidStateException("Missing orderId in webhook payload");
        }
        String eventId = text(payload, "eventId");
        if (eventId == null || eventId.isBlank()) {
            eventId = "evt_" + UUID.randomUUID();
        }
        String type = text(payload, "type");
        if (type == null) {
            type = "payment.succeeded";
        }

        WebhookEvent.Outcome outcome;
        switch (type) {
            case "payment.succeeded" -> outcome = WebhookEvent.Outcome.SUCCEEDED;
            case "payment.failed" -> outcome = WebhookEvent.Outcome.FAILED;
            case "payment.cancelled" -> outcome = WebhookEvent.Outcome.CANCELLED;
            case "payment.chargeback" -> outcome = WebhookEvent.Outcome.CHARGED_BACK;
            default -> throw new InvalidStateException("Unsupported mock event type: " + type);
        }

        Long amountMinor = payload.hasNonNull("amountMinor") ? payload.get("amountMinor").asLong() : null;
        String currency = text(payload, "currency");

        return new WebhookEvent(eventId, orderId, outcome, amountMinor, currency,
                null, type, rawBody);
    }

    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        if (signatureHeader == null || rawBody == null) {
            return false;
        }

        try {
            String computed = computeHmacSha256(rawBody, secret);
            // Constant-time comparison so response timing cannot leak the signature.
            return MessageDigest.isEqual(
                    computed.toLowerCase().getBytes(StandardCharsets.UTF_8),
                    signatureHeader.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
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

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
