package com.railbound.portal.refunds;

public enum RefundType {
    /** Merchant-initiated refund of a completed top-up. */
    REFUND,
    /** Provider-reported chargeback (money already taken back by the card network). */
    CHARGEBACK
}
