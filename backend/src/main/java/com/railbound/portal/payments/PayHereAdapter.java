package com.railbound.portal.payments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railbound.portal.common.InvalidSignatureException;
import com.railbound.portal.common.InvalidStateException;
import com.railbound.portal.common.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PayHere (payhere.lk) Checkout API adapter.
 *
 * <p>Flow: our backend builds the signed form fields (including the server-generated {@code hash});
 * the browser POSTs them to PayHere's hosted page; PayHere later calls our {@code notify_url} with a
 * form-encoded notification signed with {@code md5sig}. Only that verified notification can credit
 * coins - the browser redirect to {@code return_url} carries no payment status and grants nothing.
 *
 * <p>SECURITY
 * <ul>
 *   <li>The merchant secret never leaves the server and is never logged.</li>
 *   <li>The checkout hash is computed from the trusted order snapshot, not from client input.</li>
 *   <li>Notifications are rejected unless merchant_id matches and md5sig verifies (constant time).</li>
 *   <li>Card holder name, masked card number and expiry from the notification are NOT persisted.</li>
 * </ul>
 *
 * <p>PayHere's notify_url must be publicly reachable (use a tunnel such as ngrok while developing).
 */
@Component
public class PayHereAdapter implements PaymentGatewayAdapter {

    private static final Logger log = LoggerFactory.getLogger(PayHereAdapter.class);

    public static final String PROVIDER = "payhere";
    private static final String SANDBOX_URL = "https://sandbox.payhere.lk/pay/checkout";
    private static final String LIVE_URL = "https://www.payhere.lk/pay/checkout";
    private static final int MAX_BODY_CHARS = 16_384;

    private final boolean enabled;
    private final boolean sandbox;
    private final String merchantId;
    private final String merchantSecret;
    private final String notifyUrl;
    private final String frontendBaseUrl;
    private final ObjectMapper objectMapper;

    public PayHereAdapter(
            @Value("${app.payment.payhere.enabled:false}") boolean enabled,
            @Value("${app.payment.payhere.sandbox:true}") boolean sandbox,
            @Value("${app.payment.payhere.merchant-id:}") String merchantId,
            @Value("${app.payment.payhere.merchant-secret:}") String merchantSecret,
            @Value("${app.payment.payhere.notify-url:}") String notifyUrl,
            @Value("${app.payment.frontend-base-url:http://localhost:3000}") String frontendBaseUrl,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.sandbox = sandbox;
        this.merchantId = merchantId == null ? "" : merchantId.trim();
        this.merchantSecret = merchantSecret == null ? "" : merchantSecret.trim();
        this.notifyUrl = notifyUrl == null ? "" : notifyUrl.trim();
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
        this.objectMapper = objectMapper;

        if (enabled && !isConfigured()) {
            log.warn("PayHere is enabled but merchant-id, merchant-secret or notify-url is missing; "
                    + "the provider will stay disabled until all three are set.");
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER;
    }

    @Override
    public boolean isEnabled() {
        return enabled && isConfigured();
    }

    public boolean isSandbox() {
        return sandbox;
    }

    private boolean isConfigured() {
        return !merchantId.isEmpty() && !merchantSecret.isEmpty() && !notifyUrl.isEmpty();
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(PaymentOrder order, String checkoutToken) {
        // PayHere has no server-side "create session" call; the order id itself identifies the payment.
        String sessionId = "ph_" + order.getOrderId();
        String checkoutUrl = frontendBaseUrl + "/checkout/pay?orderId=" + urlEncode(order.getOrderId())
                + "&ct=" + urlEncode(checkoutToken);
        return new CheckoutSessionResult(sessionId, checkoutUrl);
    }

    /**
     * Builds the signed form the browser must POST to PayHere. Amount, currency and order id come
     * from the persisted order snapshot; the customer cannot influence the price.
     */
    public HostedForm buildHostedForm(PaymentOrder order, BillingDetails billing) {
        if (!isEnabled()) {
            throw new ResourceNotFoundException("Payment provider is not enabled: " + PROVIDER);
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("merchant_id", merchantId);
        fields.put("return_url", frontendBaseUrl + "/checkout/return?orderId=" + urlEncode(order.getOrderId()));
        fields.put("cancel_url", frontendBaseUrl + "/checkout/return?cancelled=1&orderId=" + urlEncode(order.getOrderId()));
        fields.put("notify_url", notifyUrl);
        fields.put("order_id", order.getOrderId());
        fields.put("items", "Railbound Real Coins: " + order.getCoinsToCredit() + " RC");
        fields.put("currency", order.getCurrency());
        fields.put("amount", PayHereSignatures.formatAmount(order.getAmountMinor()));
        fields.put("first_name", billing.firstName());
        fields.put("last_name", billing.lastName());
        fields.put("email", billing.email());
        fields.put("phone", billing.phone());
        fields.put("address", billing.address());
        fields.put("city", billing.city());
        fields.put("country", billing.country());
        // custom_1 only carries a non-sensitive SKU for easier support lookups in the PayHere dashboard.
        fields.put("custom_1", order.getBundleSku());
        fields.put("hash", PayHereSignatures.checkoutHash(
                merchantId, order.getOrderId(), order.getAmountMinor(), order.getCurrency(), merchantSecret));

        return new HostedForm(sandbox ? SANDBOX_URL : LIVE_URL, fields);
    }

    @Override
    public WebhookEvent verifyAndParseWebhook(String rawBody, String signatureHeader) {
        if (!isEnabled()) {
            throw new ResourceNotFoundException("Payment provider is not enabled: " + PROVIDER);
        }
        if (rawBody == null || rawBody.length() > MAX_BODY_CHARS) {
            throw new InvalidSignatureException("Malformed payment notification");
        }

        Map<String, String> params = parseFormBody(rawBody);

        String notifMerchantId = params.get("merchant_id");
        String orderId = params.get("order_id");
        String amount = params.get("payhere_amount");
        String currency = params.get("payhere_currency");
        String statusCode = params.get("status_code");
        String md5sig = params.get("md5sig");

        if (notifMerchantId == null || orderId == null || amount == null || currency == null
                || statusCode == null || md5sig == null) {
            throw new InvalidSignatureException("Malformed payment notification");
        }
        if (!merchantId.equals(notifMerchantId)) {
            log.warn("PayHere notification rejected: merchant_id mismatch");
            throw new InvalidSignatureException("Notification is for a different merchant");
        }

        // Signature covers merchant_id, order_id, amount, currency, status and the secret. Compare
        // using the values exactly as received - re-formatting them could hide tampering.
        String expected = PayHereSignatures.notifySignature(
                notifMerchantId, orderId, amount, currency, statusCode, merchantSecret);
        if (!PayHereSignatures.constantTimeEqualsIgnoreCase(expected, md5sig)) {
            log.warn("PayHere notification rejected: md5sig verification failed for order {}", orderId);
            throw new InvalidSignatureException("Webhook signature verification failed");
        }

        WebhookEvent.Outcome outcome;
        switch (statusCode.trim()) {
            case "2" -> outcome = WebhookEvent.Outcome.SUCCEEDED;
            case "0" -> outcome = WebhookEvent.Outcome.PENDING;
            case "-1" -> outcome = WebhookEvent.Outcome.CANCELLED;
            case "-2" -> outcome = WebhookEvent.Outcome.FAILED;
            case "-3" -> outcome = WebhookEvent.Outcome.CHARGED_BACK;
            default -> throw new InvalidStateException("Unknown PayHere status_code: " + statusCode);
        }

        String paymentId = params.get("payment_id");
        // PayHere does not send a unique event id. A payment_id + status pair identifies "this payment
        // reached this status", so exact retries dedupe but real status changes (0 -> 2) still apply.
        String eventId = (paymentId != null && !paymentId.isBlank() ? paymentId : orderId) + ":" + statusCode.trim();

        return new WebhookEvent(
                eventId,
                orderId,
                outcome,
                PayHereSignatures.parseAmountToMinor(amount),
                currency,
                paymentId,
                params.get("status_message"),
                sanitize(params));
    }

    /** Persist only non-sensitive fields; card_holder_name / card_no / card_expiry / md5sig are dropped. */
    private String sanitize(Map<String, String> params) {
        Map<String, String> safe = new LinkedHashMap<>();
        for (String key : new String[]{"order_id", "payment_id", "status_code", "status_message",
                "payhere_amount", "payhere_currency", "method", "custom_1"}) {
            if (params.containsKey(key)) {
                safe.put(key, params.get(key));
            }
        }
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /** Parses an application/x-www-form-urlencoded body; first occurrence of a key wins. */
    static Map<String, String> parseFormBody(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            try {
                result.putIfAbsent(
                        URLDecoder.decode(key, StandardCharsets.UTF_8),
                        URLDecoder.decode(value, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                throw new InvalidSignatureException("Malformed payment notification");
            }
        }
        return result;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
