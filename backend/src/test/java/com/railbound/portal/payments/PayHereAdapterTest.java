package com.railbound.portal.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railbound.portal.common.InvalidSignatureException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Adapter-level tests; no database or Spring context needed. */
class PayHereAdapterTest {

    static final String MID = "1211149";
    static final String SECRET = "test-secret-123";
    static final String NOTIFY = "https://example.test/api/v1/payments/webhooks/payhere";

    private PayHereAdapter adapter() {
        return new PayHereAdapter(true, true, MID, SECRET, NOTIFY, "http://localhost:3000", new ObjectMapper());
    }

    /** Builds a form-encoded notification exactly as PayHere would send it. */
    static String notification(String merchantId, String orderId, String amount, String currency, String status, String paymentId) {
        String sig = PayHereSignatures.notifySignature(merchantId, orderId, amount, currency, status, SECRET);
        return "merchant_id=" + merchantId + "&order_id=" + orderId + "&payment_id=" + paymentId
                + "&payhere_amount=" + amount + "&payhere_currency=" + currency + "&status_code=" + status
                + "&md5sig=" + sig + "&card_holder_name=Test+User&card_no=************1292&card_expiry=12%2F30"
                + "&method=VISA&status_message=Successfully+received";
    }

    private PaymentOrder order() {
        return new PaymentOrder("ord_abc", "usr_1", "bundle_rc_1000", 1150, 999, "USD", "payhere", "idem", null);
    }

    private BillingDetails billing() {
        return new BillingDetails("Kasun", "Perera", "kasun@example.com", "0771234567", "12 Galle Road", "Colombo", "Sri Lanka");
    }

    @Test
    void hostedFormCarriesServerComputedHashAndAmount() {
        HostedForm form = adapter().buildHostedForm(order(), billing());
        assertThat(form.action()).isEqualTo("https://sandbox.payhere.lk/pay/checkout");
        assertThat(form.fields().get("amount")).isEqualTo("9.99");
        assertThat(form.fields().get("currency")).isEqualTo("USD");
        assertThat(form.fields().get("order_id")).isEqualTo("ord_abc");
        assertThat(form.fields().get("notify_url")).isEqualTo(NOTIFY);
        assertThat(form.fields().get("hash")).isEqualTo(PayHereSignatures.checkoutHash(MID, "ord_abc", 999, "USD", SECRET));
        assertThat(form.fields().toString()).doesNotContain(SECRET);
    }

    @Test
    void validNotificationIsParsed() {
        WebhookEvent event = adapter().verifyAndParseWebhook(notification(MID, "ord_abc", "9.99", "USD", "2", "320032200001"), null);
        assertThat(event.outcome()).isEqualTo(WebhookEvent.Outcome.SUCCEEDED);
        assertThat(event.orderId()).isEqualTo("ord_abc");
        assertThat(event.amountMinor()).isEqualTo(999L);
        assertThat(event.currency()).isEqualTo("USD");
        assertThat(event.eventId()).isEqualTo("320032200001:2");
        // card data must never be persisted
        assertThat(event.sanitizedPayload()).doesNotContain("card_no").doesNotContain("Test User").doesNotContain("md5sig");
    }

    @Test
    void statusCodesMapToOutcomes() {
        assertThat(adapter().verifyAndParseWebhook(notification(MID, "o", "1.00", "USD", "0", "p1"), null).outcome())
                .isEqualTo(WebhookEvent.Outcome.PENDING);
        assertThat(adapter().verifyAndParseWebhook(notification(MID, "o", "1.00", "USD", "-1", "p1"), null).outcome())
                .isEqualTo(WebhookEvent.Outcome.CANCELLED);
        assertThat(adapter().verifyAndParseWebhook(notification(MID, "o", "1.00", "USD", "-2", "p1"), null).outcome())
                .isEqualTo(WebhookEvent.Outcome.FAILED);
        assertThat(adapter().verifyAndParseWebhook(notification(MID, "o", "1.00", "USD", "-3", "p1"), null).outcome())
                .isEqualTo(WebhookEvent.Outcome.CHARGED_BACK);
    }

    @Test
    void forgedOrTamperedNotificationsAreRejected() {
        String good = notification(MID, "ord_abc", "9.99", "USD", "2", "p1");
        // attacker lowers the amount but cannot recompute md5sig
        assertThatThrownBy(() -> adapter().verifyAndParseWebhook(good.replace("payhere_amount=9.99", "payhere_amount=0.01"), null))
                .isInstanceOf(InvalidSignatureException.class);
        // attacker flips a failure into a success
        String failed = notification(MID, "ord_abc", "9.99", "USD", "-2", "p1");
        assertThatThrownBy(() -> adapter().verifyAndParseWebhook(failed.replace("status_code=-2", "status_code=2"), null))
                .isInstanceOf(InvalidSignatureException.class);
        // missing signature
        assertThatThrownBy(() -> adapter().verifyAndParseWebhook("merchant_id=" + MID + "&order_id=x", null))
                .isInstanceOf(InvalidSignatureException.class);
        // different merchant
        assertThatThrownBy(() -> adapter().verifyAndParseWebhook(notification("999", "ord_abc", "9.99", "USD", "2", "p1"), null))
                .isInstanceOf(InvalidSignatureException.class);
    }

    @Test
    void adapterStaysDisabledWithoutFullConfiguration() {
        assertThat(new PayHereAdapter(true, true, "", SECRET, NOTIFY, "http://localhost:3000", new ObjectMapper()).isEnabled()).isFalse();
        assertThat(new PayHereAdapter(true, true, MID, SECRET, "", "http://localhost:3000", new ObjectMapper()).isEnabled()).isFalse();
        assertThat(new PayHereAdapter(false, true, MID, SECRET, NOTIFY, "http://localhost:3000", new ObjectMapper()).isEnabled()).isFalse();
        assertThat(adapter().isEnabled()).isTrue();
    }

    @Test
    void billingDetailsAreValidated() {
        assertThat(billing().validated().email()).isEqualTo("kasun@example.com");
        assertThatThrownBy(() -> new BillingDetails("", "P", "a@b.co", "0771234567", "x", "y", "z").validated())
                .hasMessageContaining("First name");
        assertThatThrownBy(() -> new BillingDetails("K", "P", "not-an-email", "0771234567", "x", "y", "z").validated())
                .hasMessageContaining("email");
        assertThatThrownBy(() -> new BillingDetails("K", "P", "a@b.co", "abc", "x", "y", "z").validated())
                .hasMessageContaining("phone");
    }
}
