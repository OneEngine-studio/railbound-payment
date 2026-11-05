package com.railbound.portal.refunds;

public enum RefundStatus {
    /** The player has already spent the coins; a human must decide how to resolve it. */
    NEEDS_REVIEW,
    /** Coins have been removed from the wallet; for REFUND cases the money refund is still to be confirmed. */
    COINS_REVERSED,
    /** Fully resolved. */
    COMPLETED
}
