package com.railbound.portal.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Stripe Checkout. Sessions are created over Stripe's REST API; fulfilment only ever happens from a webhook whose
 * Stripe-Signature header verifies against the endpoint secret. Both keys come from the environment and are never logged.
 */
@Component
public class StripePaymentAdapter implements PaymentGatewayAdapter {

    private static final long SIGNATURE_TOLERANCE_SECONDS = 300;

    private final String secretKey;
    private final String webhookSecret;
    private final String apiBase;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public StripePaymentAdapter(
            @Value("${app.payment.stripe.secret-key:}") String secretKey,
            @Value("${app.payment.stripe.webhook-secret:}") String webhookSecret,
            @Value("${app.payment.stripe.api-base:https://api.stripe.com}") String apiBase,
            ObjectMapper mapper) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.apiBase = apiBase;
        this.mapper = mapper;
    }

    @Override
    public String getProviderName() {
        return "stripe";
    }

    public boolean isConfigured() {
        return !secretKey.isBlank() && !webhookSecret.isBlank();
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(PaymentOrder order, String returnUrl) {
        if (!isConfigured()) {
            throw new IllegalStateException("Stripe is not configured on this server");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("mode", "payment");
        form.put("client_reference_id", order.getOrderId());
        form.put("metadata[orderId]", order.getOrderId());
        form.put("success_url", returnUrl + "&result=success");
        form.put("cancel_url", returnUrl + "&result=cancelled");
        form.put("line_items[0][quantity]", "1");
        form.put("line_items[0][price_data][currency]", order.getCurrency().toLowerCase());
        form.put("line_items[0][price_data][unit_amount]", Long.toString(order.getAmountMinor()));
        form.put("line_items[0][price_data][product_data][name]", "Coin bundle " + order.getBundleSku());
        form.put("expires_at", Long.toString(order.getExpiresAt().getEpochSecond()));

        String body = form.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + "/v1/checkout/sessions"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + secretKey)
                .header("Content-Type", "application/x-www-form-urlencoded")
                // The same order never creates two sessions, even if the request is retried.
                .header("Idempotency-Key", "checkout-" + order.getOrderId())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Stripe rejected the checkout request (HTTP " + response.statusCode() + ")");
            }
            JsonNode json = mapper.readTree(response.body());
            String id = json.path("id").asText("");
            String url = json.path("url").asText("");
            if (id.isEmpty() || !url.startsWith("https://")) {
                throw new IllegalStateException("Stripe returned an unusable checkout session");
            }
            return new CheckoutSessionResult(id, url);
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach Stripe", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stripe request interrupted", e);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        return verifySignature(rawBody, signatureHeader, webhookSecret, Instant.now());
    }

    /** Stripe-Signature is "t=<unix>,v1=<hmac>[,v1=...]"; the HMAC covers "<t>.<raw body>". Visible for tests. */
    static boolean verifySignature(String rawBody, String header, String secret, Instant now) {
        if (rawBody == null || header == null || secret == null || secret.isBlank()) {
            return false;
        }
        String timestamp = null;
        java.util.List<String> signatures = new java.util.ArrayList<>();
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("t")) timestamp = kv[1];
            else if (kv[0].equals("v1")) signatures.add(kv[1]);
        }
        if (timestamp == null || signatures.isEmpty()) {
            return false;
        }
        long t;
        try {
            t = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(now.getEpochSecond() - t) > SIGNATURE_TOLERANCE_SECONDS) {
            return false;
        }
        byte[] expected;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            expected = mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
        for (String candidate : signatures) {
            byte[] given;
            try {
                given = HexFormat.of().parseHex(candidate);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (MessageDigest.isEqual(expected, given)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<WebhookEvent> parseWebhook(JsonNode payload) {
        String type = payload.path("type").asText("");
        // Only a completed, paid Checkout Session credits coins. Everything else is acknowledged and ignored.
        if (!type.equals("checkout.session.completed") && !type.equals("checkout.session.async_payment_succeeded")) {
            return Optional.empty();
        }
        JsonNode session = payload.path("data").path("object");
        if (!"paid".equals(session.path("payment_status").asText(""))) {
            return Optional.empty();
        }
        String orderId = session.path("metadata").path("orderId").asText(session.path("client_reference_id").asText(""));
        if (orderId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new WebhookEvent(
                payload.path("id").asText(""),
                orderId,
                session.path("id").asText(""),
                session.has("amount_total") ? session.path("amount_total").asLong() : null,
                session.path("currency").asText("")));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
