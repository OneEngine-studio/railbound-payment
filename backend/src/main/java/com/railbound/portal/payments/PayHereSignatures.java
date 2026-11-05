package com.railbound.portal.payments;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Pure (framework-free) implementation of PayHere's two MD5 signature schemes, so they can be
 * unit-tested in isolation. Formulas are taken from PayHere's Checkout API documentation:
 *
 * <pre>
 *   checkout hash = UPPER(md5(merchant_id + order_id + amount + currency + UPPER(md5(merchant_secret))))
 *   notify md5sig = UPPER(md5(merchant_id + order_id + payhere_amount + payhere_currency
 *                             + status_code + UPPER(md5(merchant_secret))))
 * </pre>
 *
 * SECURITY NOTES
 * - The checkout hash MUST be generated on the server: it is derived from the merchant secret and
 *   is what stops a customer from editing the price in the browser.
 * - The notify signature is compared in constant time. MD5 is dictated by PayHere's protocol; it is
 *   acceptable here only because the merchant secret is mixed in (it is not used for passwords).
 * - The amount string used for the checkout hash must be byte-for-byte identical to the amount
 *   field posted to PayHere, so both come from {@link #formatAmount(long)}.
 */
public final class PayHereSignatures {

    private PayHereSignatures() {}

    /** Upper-case hex MD5 of the UTF-8 bytes of the input. */
    public static String md5Upper(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 is mandatory in every JRE; this cannot happen in practice.
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    /**
     * Converts integer minor units (e.g. 999 cents) to the plain decimal string PayHere expects
     * ("9.99"): always two decimals, '.' separator, no thousands separators. Integer arithmetic
     * only, so no floating point rounding can ever alter the amount that gets hashed.
     */
    public static String formatAmount(long amountMinor) {
        return BigDecimal.valueOf(amountMinor, 2).toPlainString();
    }

    /**
     * Parses a PayHere "payhere_amount" string (e.g. "9.99", "1000.00") to minor units.
     * Returns null when the value is not a valid non-negative amount with at most two decimals,
     * so that a malformed value is treated as a mismatch rather than silently rounded.
     */
    public static Long parseAmountToMinor(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(amount.trim());
            if (value.signum() < 0) {
                return null;
            }
            BigDecimal minor = value.movePointRight(2);
            // Reject values with more than two decimals instead of rounding them.
            if (minor.stripTrailingZeros().scale() > 0) {
                return null;
            }
            return minor.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    /** Hash posted with the checkout form. */
    public static String checkoutHash(String merchantId, String orderId, long amountMinor,
                                      String currency, String merchantSecret) {
        return md5Upper(merchantId + orderId + formatAmount(amountMinor) + currency
                + md5Upper(merchantSecret));
    }

    /** Signature PayHere should have sent with a payment notification. */
    public static String notifySignature(String merchantId, String orderId, String payhereAmount,
                                         String payhereCurrency, String statusCode,
                                         String merchantSecret) {
        return md5Upper(merchantId + orderId + payhereAmount + payhereCurrency + statusCode
                + md5Upper(merchantSecret));
    }

    /** Constant-time, case-insensitive comparison of two hex signatures. */
    public static boolean constantTimeEqualsIgnoreCase(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.trim().toUpperCase().getBytes(StandardCharsets.UTF_8),
                b.trim().toUpperCase().getBytes(StandardCharsets.UTF_8));
    }
}
