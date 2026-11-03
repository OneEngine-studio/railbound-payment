package com.railbound.portal.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.railbound.portal.common.PaymentConfigurationException;
import com.railbound.portal.common.PaymentProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * Thin client for PayHere Merchant APIs (Retrieval + Refund).
 *
 * <p>These calls are made <strong>outside</strong> database transactions. A
 * network timeout must never hold a MongoDB transaction open, and a refund must
 * be recorded in a {@code refund_cases} document before/after the provider call
 * so support staff can reconcile an ambiguous result.</p>
 *
 * <p>Credentials here are App ID / App Secret (distinct from the Checkout API's
 * merchant id/secret). They are only needed for reconciliation and refunds; the
 * checkout flow works without them.</p>
 */
@Component
public class PayHereMerchantApiClient {

    private static final Logger log = LoggerFactory.getLogger(PayHereMerchantApiClient.class);

    private final String appId;
    private final String appSecret;
    private final String apiBaseUrl;
    private final RestClient restClient;

    private volatile String cachedAccessToken;
    private volatile Instant cachedTokenExpiry = Instant.EPOCH;

    public PayHereMerchantApiClient(
            @Value("${app.payment.payhere.app-id:}") String appId,
            @Value("${app.payment.payhere.app-secret:}") String appSecret,
            @Value("${app.payment.payhere.sandbox:true}") boolean sandbox,
            @Value("${app.payment.payhere.live-api-base-url:https://www.payhere.lk}") String liveApiBaseUrl,
            @Value("${app.payment.payhere.sandbox-api-base-url:https://sandbox.payhere.lk}") String sandboxApiBaseUrl,
            @Value("${app.payment.payhere.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.payment.payhere.read-timeout-ms:15000}") int readTimeoutMs) {
        this.appId = appId == null ? "" : appId.trim();
        this.appSecret = appSecret == null ? "" : appSecret.trim();
        this.apiBaseUrl = sandbox ? sandboxApiBaseUrl : liveApiBaseUrl;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(this.apiBaseUrl)
                .requestFactory(factory)
                .build();
    }

    public boolean isConfigured() {
        return !appId.isBlank() && !appSecret.isBlank();
    }

    /**
     * Looks up the payment by merchant order id.
     * PayHere explicitly does not enforce order id uniqueness, so a "RECEIVED"
     * record is preferred when several records exist.
     */
    public Optional<PayHerePaymentRecord> retrievePayment(String orderId) {
        ensureConfigured();
        try {
            JsonNode response = withAccessToken(token ->
                    restClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/merchant/v1/payment/search")
                                    .queryParam("order_id", orderId)
                                    .build())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .body(JsonNode.class));

            if (response == null || response.path("status").asInt(-1) != 1) {
                return Optional.empty();
            }
            JsonNode data = response.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return Optional.empty();
            }

            JsonNode selected = null;
            for (JsonNode candidate : data) {
                if ("RECEIVED".equalsIgnoreCase(candidate.path("status").asText())) {
                    selected = candidate;
                    break;
                }
            }
            if (selected == null) {
                selected = data.get(0);
            }

            return Optional.of(new PayHerePaymentRecord(
                    selected.path("payment_id").asText(),
                    selected.path("order_id").asText(orderId),
                    selected.path("status").asText(),
                    selected.path("amount").decimalValue(),
                    selected.path("currency").asText(),
                    selected.path("payment_method").path("method").asText(""),
                    selected.toString()
            ));
        } catch (PaymentProviderException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("PayHere retrieval API call failed for order {}: {}", orderId, e.getMessage());
            throw new PaymentProviderException("PayHere retrieval API failed: " + safeMessage(e), e);
        }
    }

    /**
     * Requests a full or partial refund.
     *
     * @param amountMinor null for a full refund, otherwise the partial amount
     */
    public PayHereRefundResult refund(String paymentId, Long amountMinor, String description) {
        ensureConfigured();
        if (paymentId == null || paymentId.isBlank()) {
            throw new PaymentConfigurationException("Cannot refund without a PayHere payment_id");
        }

        try {
            String body = buildRefundBody(paymentId, amountMinor, description);
            JsonNode response = withAccessToken(token ->
                    restClient.post()
                            .uri("/merchant/v1/payment/refund")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(JsonNode.class));

            if (response == null) {
                return new PayHereRefundResult(false, null, "Empty response from PayHere");
            }
            int status = response.path("status").asInt(-1);
            String message = response.path("msg").asText("");
            if (status == 1) {
                String refundId = response.path("data").isNull() ? null : response.path("data").asText();
                log.info("PayHere refund accepted for payment {}: refundId={}", paymentId, refundId);
                return new PayHereRefundResult(true, refundId, message);
            }
            log.warn("PayHere refund rejected for payment {}: status={}, msg={}", paymentId, status, message);
            return new PayHereRefundResult(false, null, message.isBlank() ? "Refund rejected" : message);
        } catch (RestClientException e) {
            log.error("PayHere refund API call failed for payment {}: {}", paymentId, e.getMessage());
            throw new PaymentProviderException("PayHere refund API failed: " + safeMessage(e), e);
        }
    }

    private String buildRefundBody(String paymentId, Long amountMinor, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"payment_id\":\"").append(jsonEscape(paymentId)).append('"');
        if (description != null && !description.isBlank()) {
            sb.append(",\"description\":\"").append(jsonEscape(description)).append('"');
        }
        if (amountMinor != null) {
            // PayHere expects a decimal amount string for partial refunds.
            BigDecimal amount = BigDecimal.valueOf(amountMinor, 2)
                    .setScale(2, RoundingMode.UNNECESSARY);
            sb.append(",\"amount\":\"").append(amount.toPlainString()).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private JsonNode withAccessToken(TokenCall call) {
        String token = getAccessToken(false);
        try {
            return call.execute(token);
        } catch (HttpClientErrorException.Unauthorized e) {
            // Tokens expire after ~10 minutes; refresh once on 401.
            log.info("PayHere access token rejected; refreshing once");
            token = getAccessToken(true);
            return call.execute(token);
        }
    }

    private synchronized String getAccessToken(boolean forceRefresh) {
        if (!forceRefresh && cachedAccessToken != null && Instant.now().isBefore(cachedTokenExpiry)) {
            return cachedAccessToken;
        }
        try {
            String basic = Base64.getEncoder().encodeToString(
                    (appId + ":" + appSecret).getBytes(StandardCharsets.UTF_8));

            JsonNode response = restClient.post()
                    .uri("/merchant/v1/oauth/token")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials")
                    .retrieve()
                    .body(JsonNode.class);

            String token = response == null ? null : response.path("access_token").asText(null);
            if (token == null || token.isBlank()) {
                throw new PaymentProviderException("PayHere OAuth response did not include an access_token");
            }
            long expiresIn = Math.max(60, response.path("expires_in").asLong(600));
            // Refresh a little early to avoid a race with a request in flight.
            cachedAccessToken = token;
            cachedTokenExpiry = Instant.now().plusSeconds(expiresIn - 30);
            return token;
        } catch (RestClientException e) {
            cachedAccessToken = null;
            cachedTokenExpiry = Instant.EPOCH;
            throw new PaymentProviderException("PayHere OAuth failed: " + safeMessage(e), e);
        }
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new PaymentConfigurationException(
                    "PayHere Merchant API is not configured. Set PAYHERE_APP_ID and PAYHERE_APP_SECRET " +
                    "to use reconciliation or refunds.");
        }
    }

    /** Never echo credentials/stack traces to API consumers. */
    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }

    private static String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @FunctionalInterface
    private interface TokenCall {
        JsonNode execute(String token);
    }

    public record PayHerePaymentRecord(
            String paymentId,
            String orderId,
            String status,
            BigDecimal amount,
            String currency,
            String method,
            String raw
    ) {}

    public record PayHereRefundResult(boolean success, String providerRefundId, String message) {}
}
