package com.railbound.portal.payments;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests (no Spring, no database) for PayHere's checkout hash and notification signature. */
class PayHereSignaturesTest {

    private static final String MERCHANT_ID = "1211149";
    private static final String SECRET = "abcSECRET123";

    @Test
    void checkoutHashMatchesDocumentedFormula() {
        // hash = UPPER(md5(merchant_id + order_id + amount + currency + UPPER(md5(secret))))
        String expected = PayHereSignatures.md5Upper(
                MERCHANT_ID + "ord_1234" + "9.99" + "USD" + PayHereSignatures.md5Upper(SECRET));
        assertThat(PayHereSignatures.checkoutHash(MERCHANT_ID, "ord_1234", 999, "USD", SECRET)).isEqualTo(expected);
        assertThat(expected).hasSize(32).isUpperCase();
    }

    @Test
    void amountIsFormattedWithTwoDecimalsAndNoSeparators() {
        assertThat(PayHereSignatures.formatAmount(999)).isEqualTo("9.99");
        assertThat(PayHereSignatures.formatAmount(100000)).isEqualTo("1000.00");
        assertThat(PayHereSignatures.formatAmount(5)).isEqualTo("0.05");
    }

    @Test
    void amountParsingIsStrict() {
        assertThat(PayHereSignatures.parseAmountToMinor("9.99")).isEqualTo(999L);
        assertThat(PayHereSignatures.parseAmountToMinor("1000.00")).isEqualTo(100000L);
        assertThat(PayHereSignatures.parseAmountToMinor("9.999")).isNull();
        assertThat(PayHereSignatures.parseAmountToMinor("-1.00")).isNull();
        assertThat(PayHereSignatures.parseAmountToMinor("abc")).isNull();
        assertThat(PayHereSignatures.parseAmountToMinor(null)).isNull();
    }

    @Test
    void notificationSignatureChangesWhenAnyFieldIsTampered() {
        String good = PayHereSignatures.notifySignature(MERCHANT_ID, "ord_1", "9.99", "USD", "2", SECRET);
        assertThat(PayHereSignatures.notifySignature(MERCHANT_ID, "ord_1", "0.01", "USD", "2", SECRET)).isNotEqualTo(good);
        assertThat(PayHereSignatures.notifySignature(MERCHANT_ID, "ord_2", "9.99", "USD", "2", SECRET)).isNotEqualTo(good);
        assertThat(PayHereSignatures.notifySignature(MERCHANT_ID, "ord_1", "9.99", "LKR", "2", SECRET)).isNotEqualTo(good);
        assertThat(PayHereSignatures.notifySignature(MERCHANT_ID, "ord_1", "9.99", "USD", "-2", SECRET)).isNotEqualTo(good);
        assertThat(PayHereSignatures.notifySignature(MERCHANT_ID, "ord_1", "9.99", "USD", "2", "other")).isNotEqualTo(good);
    }

    @Test
    void constantTimeCompareIsCaseInsensitiveAndNullSafe() {
        String sig = PayHereSignatures.notifySignature(MERCHANT_ID, "ord_1", "9.99", "USD", "2", SECRET);
        assertThat(PayHereSignatures.constantTimeEqualsIgnoreCase(sig, sig.toLowerCase())).isTrue();
        assertThat(PayHereSignatures.constantTimeEqualsIgnoreCase(sig, "00" + sig.substring(2))).isFalse();
        assertThat(PayHereSignatures.constantTimeEqualsIgnoreCase(null, sig)).isFalse();
    }
}
