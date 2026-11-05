package com.railbound.portal.payments;

import com.railbound.portal.common.InvalidSignatureException;
import com.railbound.portal.refunds.RefundCase;
import com.railbound.portal.refunds.RefundService;
import com.railbound.portal.refunds.RefundStatus;
import com.railbound.portal.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end PayHere fulfillment tests against the real service layer and MongoDB replica set
 * (start MongoDB first, as for the other integration tests). PayHere notifications are built with
 * the same md5sig algorithm PayHere uses, so no network access to PayHere is needed.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.payment.payhere.enabled=true",
        "app.payment.payhere.merchant-id=" + PayHereAdapterTest.MID,
        "app.payment.payhere.merchant-secret=" + PayHereAdapterTest.SECRET,
        "app.payment.payhere.notify-url=" + PayHereAdapterTest.NOTIFY
})
class PayHerePaymentFlowTests {

    @Autowired private PaymentService paymentService;
    @Autowired private WalletService walletService;
    @Autowired private RefundService refundService;
    @Autowired private PaymentOrderRepository orderRepository;

    private static final String SKU = "bundle_rc_1000"; // 1150 coins for 9.99 USD (seeded)

    private String newUser() {
        return "usr_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private PaymentOrder newOrder(String userId) {
        return paymentService.startCheckout(userId, SKU, "payhere", "idem_" + UUID.randomUUID()).order();
    }

    private String body(PaymentOrder order, String amount, String status, String paymentId) {
        return PayHereAdapterTest.notification(PayHereAdapterTest.MID, order.getOrderId(), amount, "USD", status, paymentId);
    }

    private long balance(String userId) {
        return walletService.getOrCreateWallet(userId).getRealCoins();
    }

    private PaymentOrderStatus statusOf(PaymentOrder order) {
        return orderRepository.findByOrderId(order.getOrderId()).orElseThrow().getStatus();
    }

    @Test
    void verifiedNotificationCreditsExactlyOnce() {
        String userId = newUser();
        PaymentOrder order = newOrder(userId);
        assertThat(order.getProvider()).isEqualTo("payhere");

        paymentService.processWebhook("payhere", body(order, "9.99", "2", "pay_1"), null);
        assertThat(balance(userId)).isEqualTo(1150L);
        assertThat(statusOf(order)).isEqualTo(PaymentOrderStatus.COMPLETED);

        // exact retry (same payment + status) and a second success for the same order: no extra credit
        paymentService.processWebhook("payhere", body(order, "9.99", "2", "pay_1"), null);
        paymentService.processWebhook("payhere", body(order, "9.99", "2", "pay_2"), null);
        assertThat(balance(userId)).isEqualTo(1150L);
        assertThat(walletService.verifyLedgerInvariant(userId)).isTrue();
    }

    @Test
    void forgedNotificationCreditsNothing() {
        String userId = newUser();
        PaymentOrder order = newOrder(userId);
        String forged = body(order, "9.99", "2", "pay_x").replaceAll("md5sig=[0-9A-Fa-f]+", "md5sig=" + "0".repeat(32));

        assertThatThrownBy(() -> paymentService.processWebhook("payhere", forged, null))
                .isInstanceOf(InvalidSignatureException.class);
        assertThat(balance(userId)).isZero();
        assertThat(statusOf(order)).isEqualTo(PaymentOrderStatus.PENDING);
    }

    @Test
    void wrongAmountIsParkedForReviewAndNeverCredited() {
        String userId = newUser();
        PaymentOrder order = newOrder(userId);

        paymentService.processWebhook("payhere", body(order, "1.00", "2", "pay_cheap"), null);
        assertThat(balance(userId)).isZero();
        assertThat(statusOf(order)).isEqualTo(PaymentOrderStatus.REVIEW_REQUIRED);

        // a human verified the real payment in the PayHere dashboard and approves it
        paymentService.resolveReview(order.getOrderId(), true, "Checked in PayHere dashboard", "usr_admin_test");
        assertThat(balance(userId)).isEqualTo(1150L);
        assertThat(statusOf(order)).isEqualTo(PaymentOrderStatus.COMPLETED);
    }

    @Test
    void paymentAfterCancelOrFailureStillCredits() {
        String userId = newUser();
        PaymentOrder order = newOrder(userId);

        paymentService.processWebhook("payhere", body(order, "9.99", "-2", "pay_try1"), null);
        assertThat(statusOf(order)).isEqualTo(PaymentOrderStatus.FAILED);
        assertThat(balance(userId)).isZero();

        paymentService.processWebhook("payhere", body(order, "9.99", "2", "pay_try2"), null);
        assertThat(statusOf(order)).isEqualTo(PaymentOrderStatus.COMPLETED);
        assertThat(balance(userId)).isEqualTo(1150L);
    }

    @Test
    void hostedFormRequiresTheOneTimeCheckoutToken() {
        String userId = newUser();
        CheckoutStart start = paymentService.startCheckout(userId, SKU, "payhere", "idem_" + UUID.randomUUID());
        String url = start.checkoutUrl();
        assertThat(url).contains("/checkout/pay?orderId=" + start.order().getOrderId());
        String token = url.substring(url.indexOf("ct=") + 3);

        BillingDetails billing = new BillingDetails("Kasun", "Perera", "kasun@example.com", "0771234567", "12 Galle Rd", "Colombo", "Sri Lanka");

        HostedForm form = paymentService.buildHostedForm(start.order().getOrderId(), token, billing);
        assertThat(form.fields().get("amount")).isEqualTo("9.99");
        assertThat(form.fields().get("hash"))
                .isEqualTo(PayHereSignatures.checkoutHash(PayHereAdapterTest.MID, start.order().getOrderId(), 999, "USD", PayHereAdapterTest.SECRET));

        assertThatThrownBy(() -> paymentService.buildHostedForm(start.order().getOrderId(), "wrong-token", billing))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> paymentService.buildHostedForm(start.order().getOrderId(), null, billing))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void refundReversesCoinsAndKeepsLedgerBalanced() {
        String userId = newUser();
        PaymentOrder order = newOrder(userId);
        paymentService.processWebhook("payhere", body(order, "9.99", "2", "pay_r"), null);

        RefundCase refund = refundService.createRefund(order.getOrderId(), "Customer request", "usr_admin_test");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COINS_REVERSED);
        assertThat(balance(userId)).isZero();
        assertThat(walletService.verifyLedgerInvariant(userId)).isTrue();
        assertThat(statusOf(order)).isEqualTo(PaymentOrderStatus.REFUND_PENDING);

        RefundCase done = refundService.markProviderRefunded(refund.getRefundId(), "PH-REFUND-1", "usr_admin_test");
        assertThat(done.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(statusOf(order)).isEqualTo(PaymentOrderStatus.REFUNDED);
    }

    @Test
    void refundNeverCreatesNegativeBalance() {
        String userId = newUser();
        PaymentOrder order = newOrder(userId);
        paymentService.processWebhook("payhere", body(order, "9.99", "2", "pay_s"), null);
        walletService.debitRealCoins(userId, 450, "TEST", "spend_" + UUID.randomUUID(), "player spent coins");

        RefundCase refund = refundService.createRefund(order.getOrderId(), "Customer request", "usr_admin_test");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.NEEDS_REVIEW);
        assertThat(balance(userId)).isEqualTo(700L); // untouched, never negative
        assertThat(walletService.verifyLedgerInvariant(userId)).isTrue();
    }

    @Test
    void chargebackReversesCoinsAutomatically() {
        String userId = newUser();
        PaymentOrder order = newOrder(userId);
        paymentService.processWebhook("payhere", body(order, "9.99", "2", "pay_c"), null);
        paymentService.processWebhook("payhere", body(order, "9.99", "-3", "pay_c"), null);

        assertThat(statusOf(order)).isEqualTo(PaymentOrderStatus.CHARGED_BACK);
        assertThat(balance(userId)).isZero();
        assertThat(walletService.verifyLedgerInvariant(userId)).isTrue();
    }
}
