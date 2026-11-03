package com.railbound.portal.payments;

import com.railbound.portal.common.InvalidSignatureException;
import com.railbound.portal.common.InvalidStateException;
import com.railbound.portal.identity.User;
import com.railbound.portal.identity.UserRepository;
import com.railbound.portal.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end backend tests for the real PayHere adapter, exercising the same
 * code path a sandbox callback would use:
 * create order -> signed form -> verified webhook -> exactly one wallet credit.
 */
@SpringBootTest
class PayHerePaymentFlowTests {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PayHereAdapter payHereAdapter;

    private String userId;

    @BeforeEach
    void setUp() {
        userId = "usr_ph_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userRepository.save(new User(userId, "nakama_" + userId, "PayHere Tester", "PLAYER"));
        walletService.getOrCreateWallet(userId);
    }

    @Test
    @DisplayName("PayHere checkout stores an immutable 1150 RC / 9.99 USD snapshot and a hosted bridge URL")
    void createsPayHereCheckoutWithTrustedSnapshot() {
        PaymentOrder order = paymentService.createCheckoutSession(
                userId, "bundle_rc_1000", "payhere", "idem_ph_" + UUID.randomUUID());

        assertEquals("payhere", order.getProvider());
        assertEquals(1150, order.getCoinsToCredit());
        assertEquals(999, order.getAmountMinor());
        assertEquals("USD", order.getCurrency());
        assertNotNull(order.getCheckoutToken());
        assertTrue(order.getCheckoutUrl().contains("/checkout/hosted?orderId="),
                "Frontend must serve the auto-submit page so PayHere sees the registered domain");

        PaymentService.HostedCheckout hosted =
                paymentService.prepareHostedCheckout(order.getOrderId(), order.getCheckoutToken());

        assertTrue(hosted.requiresFormPost());
        assertEquals("https://sandbox.payhere.lk/pay/checkout", hosted.actionUrl());
        assertEquals("1211149", hosted.formFields().get("merchant_id"));
        assertEquals("9.99", hosted.formFields().get("amount"));
        assertEquals("USD", hosted.formFields().get("currency"));
        assertEquals(order.getOrderId(), hosted.formFields().get("order_id"));
        assertTrue(hosted.formFields().get("notify_url").endsWith("/api/v1/payments/webhooks/payhere"));
        assertEquals(
                payHereAdapter.computeCheckoutHash(order.getOrderId(), "9.99", "USD"),
                hosted.formFields().get("hash"));
    }

    @Test
    @DisplayName("PayHere success callback credits exactly 1150 RC and retries cannot double-credit")
    void creditsExactlyOnceAndDeduplicates() {
        PaymentOrder order = paymentService.createCheckoutSession(
                userId, "bundle_rc_1000", "payhere", "idem_ph_" + UUID.randomUUID());

        String paymentId = "32002507" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        Map<String, String> params = payHereCallback(order, "2", "9.99", "USD", paymentId);

        assertTrue(paymentService.processWebhook("payhere", "", Map.of(), params));
        assertEquals(1150, walletService.getWalletByUserId(userId).getRealCoins());
        assertTrue(walletService.verifyLedgerInvariant(userId));

        PaymentOrder completed = paymentService.getOrderForUser(order.getOrderId(), userId);
        assertEquals(PaymentOrderStatus.COMPLETED, completed.getStatus());
        assertEquals(paymentId, completed.getProviderPaymentId());

        // Exact provider retry: same payment_id + status_code -> deduplicated.
        assertTrue(paymentService.processWebhook("payhere", "", Map.of(), params));
        assertEquals(1150, walletService.getWalletByUserId(userId).getRealCoins());

        // A different payment_id for the same order must also not credit twice.
        Map<String, String> secondPayment = payHereCallback(
                order, "2", "9.99", "USD", paymentId + "9");
        assertTrue(paymentService.processWebhook("payhere", "", Map.of(), secondPayment));
        assertEquals(1150, walletService.getWalletByUserId(userId).getRealCoins());
        assertTrue(walletService.verifyLedgerInvariant(userId));
    }

    @Test
    @DisplayName("A correctly signed callback with the wrong amount is rejected")
    void rejectsWrongAmount() {
        PaymentOrder order = paymentService.createCheckoutSession(
                userId, "bundle_rc_1000", "payhere", "idem_ph_" + UUID.randomUUID());

        // The signature is valid for 1.00, but the order snapshot is 9.99.
        Map<String, String> params = payHereCallback(
                order, "2", "1.00", "USD", "999" + UUID.randomUUID());

        assertThrows(InvalidStateException.class, () ->
                paymentService.processWebhook("payhere", "", Map.of(), params));
        assertEquals(0, walletService.getWalletByUserId(userId).getRealCoins());
    }

    @Test
    @DisplayName("A correctly signed callback with the wrong currency is rejected")
    void rejectsWrongCurrency() {
        PaymentOrder order = paymentService.createCheckoutSession(
                userId, "bundle_rc_1000", "payhere", "idem_ph_" + UUID.randomUUID());

        Map<String, String> params = payHereCallback(
                order, "2", "9.99", "EUR", "998" + UUID.randomUUID());

        assertThrows(InvalidStateException.class, () ->
                paymentService.processWebhook("payhere", "", Map.of(), params));
        assertEquals(0, walletService.getWalletByUserId(userId).getRealCoins());
    }

    @Test
    @DisplayName("A forged PayHere md5sig is rejected with no state change")
    void rejectsForgedSignature() {
        PaymentOrder order = paymentService.createCheckoutSession(
                userId, "bundle_rc_1000", "payhere", "idem_ph_" + UUID.randomUUID());

        Map<String, String> params = payHereCallback(
                order, "2", "9.99", "USD", "997" + UUID.randomUUID());
        params.put("md5sig", "00000000000000000000000000000000");

        assertThrows(InvalidSignatureException.class, () ->
                paymentService.processWebhook("payhere", "", Map.of(), params));
        assertEquals(0, walletService.getWalletByUserId(userId).getRealCoins());
    }

    @Test
    @DisplayName("Pending/cancelled callbacks never credit coins")
    void nonPaidOutcomesDoNotCredit() {
        PaymentOrder cancelled = paymentService.createCheckoutSession(
                userId, "bundle_rc_500", "payhere", "idem_ph_" + UUID.randomUUID());
        Map<String, String> cancelledParams = payHereCallback(
                cancelled, "-1", "4.99", "USD", "996" + UUID.randomUUID());

        assertTrue(paymentService.processWebhook("payhere", "", Map.of(), cancelledParams));
        assertEquals(PaymentOrderStatus.CANCELLED,
                paymentService.getOrderForUser(cancelled.getOrderId(), userId).getStatus());
        assertEquals(0, walletService.getWalletByUserId(userId).getRealCoins());
    }

    private Map<String, String> payHereCallback(PaymentOrder order, String statusCode,
                                                String amount, String currency, String paymentId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("merchant_id", "1211149");
        params.put("order_id", order.getOrderId());
        params.put("payment_id", paymentId);
        params.put("payhere_amount", amount);
        params.put("payhere_currency", currency);
        params.put("status_code", statusCode);
        params.put("method", "VISA");
        params.put("md5sig", payHereAdapter.computeNotificationSignature(
                order.getOrderId(), amount, currency, statusCode));
        return params;
    }
}
