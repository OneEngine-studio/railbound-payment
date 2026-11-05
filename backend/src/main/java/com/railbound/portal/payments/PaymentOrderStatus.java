package com.railbound.portal.payments;

/**
 * Lifecycle of a fiat top-up order.
 *
 * <pre>
 *  CREATED/PENDING -> PROCESSING -> COMPLETED -> REFUND_PENDING -> REFUNDED
 *                  \-> FAILED / CANCELLED / EXPIRED
 *                  \-> REVIEW_REQUIRED (payment received but amount/currency did not match)
 *  COMPLETED -> CHARGED_BACK (provider reported a chargeback)
 * </pre>
 *
 * Money received is authoritative: a verified, matching success notification may complete an order
 * even from FAILED / CANCELLED / EXPIRED, because the customer can retry inside the same provider
 * checkout session or pay after our local session timer ran out. Once COMPLETED the order can only
 * move along the refund / chargeback path, never back to an unpaid state.
 */
public enum PaymentOrderStatus {
    CREATED,
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    EXPIRED,
    REVIEW_REQUIRED,
    REFUND_PENDING,
    REFUNDED,
    CHARGED_BACK;

    /** Returns true when moving from this state to {@code next} is a legal transition. */
    public boolean canTransitionTo(PaymentOrderStatus next) {
        if (next == null || next == this) {
            return false;
        }
        switch (this) {
            case CREATED:
                return next == PENDING || next == COMPLETED || next == FAILED || next == CANCELLED
                        || next == EXPIRED || next == REVIEW_REQUIRED;
            case PENDING:
                return next == PROCESSING || next == COMPLETED || next == FAILED || next == CANCELLED
                        || next == EXPIRED || next == REVIEW_REQUIRED;
            case PROCESSING:
                return next == COMPLETED || next == FAILED || next == CANCELLED || next == EXPIRED
                        || next == REVIEW_REQUIRED;
            case FAILED:
            case CANCELLED:
            case EXPIRED:
                // Late or retried payment inside the provider's checkout session.
                return next == COMPLETED || next == REVIEW_REQUIRED;
            case REVIEW_REQUIRED:
                return next == COMPLETED || next == FAILED;
            case COMPLETED:
                return next == REFUND_PENDING || next == CHARGED_BACK;
            case REFUND_PENDING:
                return next == REFUNDED;
            case REFUNDED:
            case CHARGED_BACK:
            default:
                return false;
        }
    }

    /** True for states in which the player has been credited (or credit has been reversed). */
    public boolean isFulfilled() {
        return this == COMPLETED || this == REFUND_PENDING || this == REFUNDED || this == CHARGED_BACK;
    }
}
