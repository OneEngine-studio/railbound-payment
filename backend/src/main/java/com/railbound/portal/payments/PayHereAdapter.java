package com.railbound.portal.payments;

import com.railbound.portal.common.InvalidSignatureException;
import com.railbound.portal.common.InvalidStateException;
import com.railbound.portal.common.PaymentConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * PayHere (Sri Lanka) Checkout API adapter.
 *
 * <p>PayHere does not use HMAC. Its integrity model is two MD5 digests:</p>
 *
 * <pre>
 * checkout hash = UPPER(MD5(merchant_id + order_id + amount + currency + UPPER(MD5(merchant_secret))))
 * notify  md5sig = UPPER(MD5(merchant_id + order_id + payhere_amount + payhere_currency + status_code + UPPER(MD5(merchant_secret))))
 * </pre>
 *
 * <p>Important operational rules:</p>
 * <ul>
 *   <li>The merchant secret never leaves the backend. The per-order hash is
 *       intentionally included in the auto-submitted form because that is how
 *       PayHere authenticates the checkout request.</li>
 *   <li>Amounts are formatted with exactly two decimals. Using the order's
 *       immutable minor-unit snapshot prevents client-side price tampering.</li>
 *   <li>Callbacks must be verified against the <em>exact</em> values PayHere
 *       posted, especially {@code payhere_amount}, because it is part of the
 *       digest.</li>
 * </ul>
 */
@Component
public class PayHereAdapter implements PaymentGatewayAdapter {

    private static final Logger log = LoggerFactory.getLogger(PayHereAdapter.class);

    /** PayHere supports these currencies for Checkout API. */
    private static final java.util.Set<String> SUPPORTED_CURRENCIES = java.util.Set.of("LKR", "USD");

    private final String merchantId;
    private final String merchantSecret;
    private final boolean sandbox;
    private final String liveCheckoutUrl;
    private final String sandboxCheckoutUrl;
    private final String defaultCurrency;

    public PayHereAdapter(
            @Value("${app.payment.payhere.merchant-id:}") String merchantId,
            @Value("${app.payment.payhere.merchant-secret:}") String merchantSecret,
            @Value("${app.payment.payhere.sandbox:true}") boolean sandbox,
            @Value("${app.payment.payhere.live-checkout-url:https://www.payhere.lk/pay/checkout}") String liveCheckoutUrl,
            @Value("${app.payment.payhere.sandbox-checkout-url:https://sandbox.payhere.lk/pay/checkout}") String sandboxCheckoutUrl,
            @Value("${app.payment.payhere.currency:USD}") String defaultCurrency) {
        this.merchantId = merchantId == null ? "" : merchantId.trim();
        this.merchantSecret = merchantSecret == null ? "" : merchantSecret.trim();
        this.sandbox = sandbox;
        this.liveCheckoutUrl = liveCheckoutUrl;
        this.sandboxCheckoutUrl = sandboxCheckoutUrl;
        this.defaultCurrency = defaultCurrency == null ? "USD" : defaultCurrency.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public String getProviderName() {
        return "payhere";
    }

    @Override
    public boolean isConfigured() {
        return !merchantId.isBlank() && !merchantSecret.isBlank();
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(PaymentOrder order, CheckoutContext context) {
        ensureConfigured();

        String currency = resolveCurrency(order.getCurrency());
        String amount = formatAmountMinor(order.getAmountMinor());
        String orderId = order.getOrderId();

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("merchant_id", merchantId);
        fields.put("return_url", context.returnUrl());
        fields.put("cancel_url", context.cancelUrl());
        fields.put("notify_url", context.notifyUrl());
        fields.put("order_id", orderId);
        fields.put("items", "Railbound Real Coins - " + order.getBundleSku()
                + " (" + order.getCoinsToCredit() + " RC)");
        fields.put("currency", currency);
        fields.put("amount", amount);

        // Customer details only pre-fill the hosted page. They are not proof of
        // identity and are never used to decide which wallet to credit.
        fields.put("first_name", safe(context.customerFirstName(), "Railbound"));
        fields.put("last_name", safe(context.customerLastName(), "Player"));
        fields.put("email", safe(context.customerEmail(), "player@railbound.invalid"));
        fields.put("phone", safe(context.customerPhone(), "0770000000"));
        fields.put("address", safe(context.customerAddress(), "No.1 Railbound Sector"));
        fields.put("city", safe(context.customerCity(), "Colombo"));
        fields.put("country", safe(context.customerCountry(), "Sri Lanka"));

        // custom_1/custom_2 are echoed back by PayHere and let support staff trace
        // a payment without trusting anything from the client.
        fields.put("custom_1", order.getUserId());
        fields.put("custom_2", order.getBundleSku());
        fields.put("platform", "Railbound Portal");
        fields.put("hash", computeCheckoutHash(orderId, amount, currency));

        // PayHere has no server-side "session" call: the checkout is the form POST.
        // A local reference is still stored to make order tracing easier.
        String localReference = "payhere_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("Prepared PayHere checkout for order {} (sandbox={}, amount={} {})",
                orderId, sandbox, amount, currency);

        return new CheckoutSessionResult(localReference, checkoutActionUrl(), Map.copyOf(fields));
    }

    @Override
    public PaymentNotification verifyAndParseNotification(WebhookRequest request) {
        ensureConfigured();

        Map<String, String> params = request.formParams() == null || request.formParams().isEmpty()
                ? parseFormBody(request.rawBody())
                : request.formParams();

        String receivedMerchantId = required(params, "merchant_id");
        String orderId = required(params, "order_id");
        String amount = required(params, "payhere_amount");
        String currency = required(params, "payhere_currency");
        String statusCode = required(params, "status_code");
        String md5sig = required(params, "md5sig");

        // A merchant-id mismatch usually means the callback was sent to the wrong
        // environment or forged; reject before touching any order.
        if (!constantTimeEquals(merchantId, receivedMerchantId)) {
            throw new InvalidSignatureException("PayHere callback merchant_id does not match this portal");
        }

        String expected = computeNotificationSignature(orderId, amount, currency, statusCode);
        if (!constantTimeEquals(expected, md5sig)) {
            log.warn("Rejected PayHere callback for order {}: md5sig mismatch", orderId);
            throw new InvalidSignatureException("PayHere md5sig verification failed");
        }

        PaymentNotification.Outcome outcome = mapStatus(statusCode);
        long amountMinor;
        try {
            amountMinor = parseAmountMinor(amount);
        } catch (ArithmeticException | NumberFormatException e) {
            throw new InvalidStateException("PayHere callback contains an invalid amount");
        }

        String paymentId = trimToNull(params.get("payment_id"));
        String eventId = paymentId != null
                ? paymentId + ":" + statusCode
                : "nopid:" + md5Hex(orderId + "|" + statusCode + "|" + amount + "|" + currency);

        String sanitized = String.format(Locale.ROOT,
                "{\"orderId\":\"%s\",\"paymentId\":\"%s\",\"statusCode\":\"%s\",\"amount\":\"%s\",\"currency\":\"%s\",\"method\":\"%s\"}",
                jsonEscape(orderId),
                jsonEscape(paymentId == null ? "" : paymentId),
                jsonEscape(statusCode),
                jsonEscape(amount),
                jsonEscape(currency),
                jsonEscape(safe(params.get("method"), "")));

        log.info("Verified PayHere callback for order {}: paymentId={}, statusCode={}, outcome={}",
                orderId, paymentId, statusCode, outcome);

        return new PaymentNotification(
                eventId,
                orderId,
                paymentId,
                outcome,
                amountMinor,
                currency.toUpperCase(Locale.ROOT),
                statusCode,
                sanitized
        );
    }

    public String checkoutActionUrl() {
        return sandbox ? sandboxCheckoutUrl : liveCheckoutUrl;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    /** Exposed for deterministic tests and operations tooling. */
    public String computeCheckoutHash(String orderId, String amount, String currency) {
        String secretHash = md5Hex(merchantSecret);
        return md5Hex(merchantId + orderId + amount + currency + secretHash);
    }

    /** Exposed for deterministic tests and operations tooling. */
    public String computeNotificationSignature(String orderId, String payhereAmount,
                                               String currency, String statusCode) {
        String secretHash = md5Hex(merchantSecret);
        return md5Hex(merchantId + orderId + payhereAmount + currency + statusCode + secretHash);
    }

    /**
     * Converts minor units to PayHere's required 2-decimal string without using
     * floating point (currency must not drift).
     */
    public static String formatAmountMinor(long amountMinor) {
        return BigDecimal.valueOf(amountMinor, 2)
                .setScale(2, RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    public static long parseAmountMinor(String amount) {
        return new BigDecimal(amount.trim())
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact();
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new PaymentConfigurationException(
                    "PayHere is not configured. Set PAYHERE_MERCHANT_ID and PAYHERE_MERCHANT_SECRET " +
                    "(and PAYHERE_SANDBOX=true for sandbox) before creating checkouts.");
        }
    }

    private String resolveCurrency(String orderCurrency) {
        String currency = orderCurrency == null || orderCurrency.isBlank()
                ? defaultCurrency
                : orderCurrency.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            throw new PaymentConfigurationException(
                    "PayHere supports only LKR or USD; order currency was " + currency);
        }
        return currency;
    }

    private PaymentNotification.Outcome mapStatus(String statusCode) {
        return switch (statusCode.trim()) {
            case "2" -> PaymentNotification.Outcome.PAID;
            case "0" -> PaymentNotification.Outcome.PENDING;
            case "-1" -> PaymentNotification.Outcome.CANCELLED;
            case "-2" -> PaymentNotification.Outcome.FAILED;
            case "-3" -> PaymentNotification.Outcome.CHARGEDBACK;
            default -> throw new InvalidStateException("Unknown PayHere status_code: " + statusCode);
        };
    }

    private static Map<String, String> parseFormBody(String rawBody) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawBody == null || rawBody.isBlank()) {
            return params;
        }
        for (String pair : rawBody.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            params.put(
                    URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return params;
    }

    private static String required(Map<String, String> params, String name) {
        String value = trimToNull(params.get(name));
        if (value == null) {
            throw new InvalidStateException("PayHere callback is missing required field: " + name);
        }
        return value;
    }

    static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            // PayHere compares uppercase hex strings for both hash and md5sig.
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)))
                    .toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM does not provide MD5", e);
        }
    }

    private static String safe(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
