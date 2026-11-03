package com.railbound.portal.payments;

/**
 * Top-up order state machine.
 *
 * <p>Happy path: CREATED -> PENDING -> PROCESSING -> COMPLETED.</p>
 * <p>Terminal failure paths: FAILED, CANCELLED, EXPIRED.</p>
 * <p>Refund lifecycle is separate: COMPLETED -> REFUND_PENDING -> REFUNDED.
 * REFUND_PENDING is intentionally non-terminal because a provider refund may be
 * accepted while the coin reversal still needs support review (for example when
 * the player has already spent the coins).</p>
 */
public enum PaymentOrderStatus {
    CREATED,
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    EXPIRED,
    REFUND_PENDING,
    REFUNDED
}
