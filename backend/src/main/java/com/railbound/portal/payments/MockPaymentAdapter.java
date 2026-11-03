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
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Development-only simulator. It exists so wallet/ledger behaviour can be
 * regression-tested without contacting a real gateway.
 *
 * <p>It is disabled by default ({@code app.payment.mock.enabled=false}) and the
 * PayHere adapter is the default provider. This prevents a production
 * deployment from accidentally accepting simulated payments.</p>
 */
@Component
public class MockPaymentAdapter implements PaymentGatewayAdapter {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentAdapter.class);

    private final boolean enabled;
    private final String secret;
    private final String checkoutBaseUrl;
    private final ObjectMapper objectMapper;

    public MockPaymentAdapter(
            @Value("${app.payment.mock.enabled:false}") boolean enabled,
            @Value("${app.payment.mock.secret:mock_webhook_secret_key_99182}") String secret,
            @Value("${app.payment.mock.checkout-base-url:http://localhost:3000/checkout/hosted-mock}") String checkoutBaseUrl,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.secret = secret;
        this.checkoutBaseUrl = checkoutBaseUrl;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public boolean isConfigured() {
        return enabled;
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(PaymentOrder order, CheckoutContext context) {
        if (!enabled) {
            throw new com.railbound.portal.common.PaymentConfigurationException(
                    "Mock payments are disabled. Set APP_PAYMENT_MOCK_ENABLED=true only for local testing.");
        }
        String sessionId = "sess_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String checkoutUrl = checkoutBaseUrl + "?orderId=" + order.getOrderId() + "&sessionId=" + sessionId;
        return new CheckoutSessionResult(sessionId, checkoutUrl, Map.of());
    }

    @Override
    public PaymentNotification verifyAndParseNotification(WebhookRequest request) {
        String signature = request.header("X-Signature");
        if (signature == null || signature.isBlank()) {
            signature = request.param("signature");
        }
        String rawBody = request.rawBody();
        if (signature == null || rawBody == null || !verifyWebhookSignature(rawBody, signature)) {
            throw new InvalidSignatureException("Mock webhook signature verification failed");
        }

        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            String orderId = payload.hasNonNull("orderId") ? payload.get("orderId").asText() : null;
            if (orderId == null || orderId.isBlank()) {
                throw new InvalidStateException("Missing orderId in mock webhook payload");
            }
            String eventId = payload.hasNonNull("eventId")
                    ? payload.get("eventId").asText()
                    : "evt_mock_" + UUID.randomUUID().toString().replace("-", "");
            String type = payload.hasNonNull("type") ? payload.get("type").asText() : "payment.succeeded";
            PaymentNotification.Outcome outcome = switch (type) {
                case "payment.succeeded" -> PaymentNotification.Outcome.PAID;
                case "payment.pending" -> PaymentNotification.Outcome.PENDING;
                case "payment.cancelled" -> PaymentNotification.Outcome.CANCELLED;
                case "payment.failed" -> PaymentNotification.Outcome.FAILED;
                case "payment.refunded" -> PaymentNotification.Outcome.REFUNDED;
                default -> throw new InvalidStateException("Unsupported mock event type: " + type);
            };
            Long amountMinor = payload.hasNonNull("amountMinor") ? payload.get("amountMinor").asLong() : null;
            String currency = payload.hasNonNull("currency") ? payload.get("currency").asText() : null;

            return new PaymentNotification(
                    eventId,
                    orderId,
                    payload.hasNonNull("paymentId") ? payload.get("paymentId").asText() : null,
                    outcome,
                    amountMinor,
                    currency,
                    String.valueOf(outcome),
                    rawBody
            );
        } catch (InvalidSignatureException | InvalidStateException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidStateException("Invalid mock webhook payload: " + e.getMessage());
        }
    }

    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        if (signatureHeader == null || rawBody == null) {
            return false;
        }
        try {
            String computed = computeHmacSha256(rawBody, secret);
            return java.security.MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.trim().getBytes(StandardCharsets.UTF_8));
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
