package com.railbound.portal.payments;

import com.railbound.portal.common.InvalidSignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for the PayHere digest format. The expected values were
 * generated independently with .NET MD5 to guard against a self-consistent but
 * wrong implementation.
 */
class PayHereAdapterTest {

    private static final String MERCHANT_ID = "1211149";
    private static final String MERCHANT_SECRET = "sandbox_secret_123";

    private PayHereAdapter adapter() {
        return new PayHereAdapter(
                MERCHANT_ID,
                MERCHANT_SECRET,
                true,
                "https://www.payhere.lk/pay/checkout",
                "https://sandbox.payhere.lk/pay/checkout",
                "USD"
        );
    }

    @Test
    @DisplayName("PayHere checkout hash matches the official MD5 formula")
    void checkoutHashMatchesOfficialAlgorithm() {
        PayHereAdapter adapter = adapter();
        String hash = adapter.computeCheckoutHash("ord_payhere_unit_test", "9.99", "USD");
        assertEquals("456F461FD5BAFCE8A23B6CA83165B9B5", hash);
    }

    @Test
    @DisplayName("PayHere md5sig is verified and normalized into a PAID notification")
    void verifiesNotificationSignature() {
        PayHereAdapter adapter = adapter();
        Map<String, String> params = baseParams();
        params.put("md5sig", "384CC5DCF021C269ABDA7845E44AE723");

        PaymentNotification notification = adapter.verifyAndParseNotification(
                new PaymentGatewayAdapter.WebhookRequest("", Map.of(), params));

        assertEquals(PaymentNotification.Outcome.PAID, notification.outcome());
        assertEquals("ord_payhere_unit_test", notification.orderId());
        assertEquals(999L, notification.amountMinor());
        assertEquals("USD", notification.currency());
        assertEquals("320025071278", notification.providerPaymentId());
        assertEquals("320025071278:2", notification.providerEventId());
    }

    @Test
    @DisplayName("Tampering with the amount invalidates the md5sig")
    void rejectsTamperedAmount() {
        PayHereAdapter adapter = adapter();
        Map<String, String> params = baseParams();
        params.put("md5sig", "384CC5DCF021C269ABDA7845E44AE723");
        params.put("payhere_amount", "1.00");

        assertThrows(InvalidSignatureException.class, () ->
                adapter.verifyAndParseNotification(
                        new PaymentGatewayAdapter.WebhookRequest("", Map.of(), params)));
    }

    @Test
    @DisplayName("A callback for a different merchant is rejected")
    void rejectsWrongMerchant() {
        PayHereAdapter adapter = adapter();
        Map<String, String> params = baseParams();
        params.put("merchant_id", "9999999");
        params.put("md5sig", "384CC5DCF021C269ABDA7845E44AE723");

        assertThrows(InvalidSignatureException.class, () ->
                adapter.verifyAndParseNotification(
                        new PaymentGatewayAdapter.WebhookRequest("", Map.of(), params)));
    }

    @Test
    @DisplayName("Missing md5sig is rejected instead of treated as success")
    void rejectsMissingSignature() {
        PayHereAdapter adapter = adapter();
        Map<String, String> params = baseParams();

        assertThrows(Exception.class, () ->
                adapter.verifyAndParseNotification(
                        new PaymentGatewayAdapter.WebhookRequest("", Map.of(), params)));
    }

    @Test
    @DisplayName("PayHere status codes map to normalized outcomes")
    void mapsStatusCodes() {
        PayHereAdapter adapter = adapter();
        assertMapping(adapter, "0", PaymentNotification.Outcome.PENDING);
        assertMapping(adapter, "-1", PaymentNotification.Outcome.CANCELLED);
        assertMapping(adapter, "-2", PaymentNotification.Outcome.FAILED);
        assertMapping(adapter, "-3", PaymentNotification.Outcome.CHARGEDBACK);
    }

    @Test
    @DisplayName("Minor-unit formatting never introduces floating point drift")
    void formatsAmountsWithoutFloatingPoint() {
        assertEquals("9.99", PayHereAdapter.formatAmountMinor(999));
        assertEquals("100.00", PayHereAdapter.formatAmountMinor(10000));
        assertEquals("0.99", PayHereAdapter.formatAmountMinor(99));
        assertEquals(999L, PayHereAdapter.parseAmountMinor("9.99"));
        assertEquals(10000L, PayHereAdapter.parseAmountMinor("100.00"));
    }

    @Test
    @DisplayName("An unconfigured adapter refuses to create checkouts")
    void unconfiguredAdapterFailsClosed() {
        PayHereAdapter empty = new PayHereAdapter(
                "", "", true,
                "https://www.payhere.lk/pay/checkout",
                "https://sandbox.payhere.lk/pay/checkout",
                "USD");
        assertFalse(empty.isConfigured());
        assertThrows(com.railbound.portal.common.PaymentConfigurationException.class, () ->
                empty.createCheckoutSession(new PaymentOrder(
                        "ord_x", "usr_x", "bundle_rc_100", 100, 99, "USD", "payhere",
                        null, java.time.Instant.now().plusSeconds(600)),
                        new PaymentGatewayAdapter.CheckoutContext(
                                "http://localhost/return", "http://localhost/cancel",
                                "http://localhost/notify", "Test", "Player",
                                "test@example.com", "0770000000",
                                "Address", "Colombo", "Sri Lanka")));
    }

    private void assertMapping(PayHereAdapter adapter, String statusCode,
                               PaymentNotification.Outcome expected) {
        Map<String, String> params = baseParams();
        params.put("status_code", statusCode);
        params.put("md5sig", adapter.computeNotificationSignature(
                params.get("order_id"), params.get("payhere_amount"),
                params.get("payhere_currency"), statusCode));

        PaymentNotification notification = adapter.verifyAndParseNotification(
                new PaymentGatewayAdapter.WebhookRequest("", Map.of(), params));
        assertEquals(expected, notification.outcome());
    }

    private Map<String, String> baseParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("merchant_id", MERCHANT_ID);
        params.put("order_id", "ord_payhere_unit_test");
        params.put("payment_id", "320025071278");
        params.put("payhere_amount", "9.99");
        params.put("payhere_currency", "USD");
        params.put("status_code", "2");
        params.put("method", "VISA");
        return params;
    }
}
