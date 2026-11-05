package com.railbound.portal.payments;

import org.junit.jupiter.api.Test;

import static com.railbound.portal.payments.PaymentOrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentOrderStatusTest {

    @Test
    void unpaidOrdersCanComplete_includingLatePaymentAfterFailureOrExpiry() {
        assertThat(PENDING.canTransitionTo(COMPLETED)).isTrue();
        assertThat(PROCESSING.canTransitionTo(COMPLETED)).isTrue();
        assertThat(FAILED.canTransitionTo(COMPLETED)).isTrue();   // customer retried inside the same PayHere session
        assertThat(EXPIRED.canTransitionTo(COMPLETED)).isTrue();  // paid after our local timer ran out
        assertThat(CANCELLED.canTransitionTo(COMPLETED)).isTrue();
    }

    @Test
    void completedOrdersNeverGoBackToUnpaid() {
        assertThat(COMPLETED.canTransitionTo(PENDING)).isFalse();
        assertThat(COMPLETED.canTransitionTo(FAILED)).isFalse();
        assertThat(COMPLETED.canTransitionTo(EXPIRED)).isFalse();
        assertThat(COMPLETED.canTransitionTo(REFUND_PENDING)).isTrue();
        assertThat(COMPLETED.canTransitionTo(CHARGED_BACK)).isTrue();
    }

    @Test
    void refundPathIsOneWay() {
        assertThat(REFUND_PENDING.canTransitionTo(REFUNDED)).isTrue();
        assertThat(REFUNDED.canTransitionTo(COMPLETED)).isFalse();
        assertThat(CHARGED_BACK.canTransitionTo(COMPLETED)).isFalse();
    }

    @Test
    void fulfilledStatesAreExactlyTheOnesThatHoldALedgerCredit() {
        assertThat(COMPLETED.isFulfilled()).isTrue();
        assertThat(REFUND_PENDING.isFulfilled()).isTrue();
        assertThat(REFUNDED.isFulfilled()).isTrue();
        assertThat(CHARGED_BACK.isFulfilled()).isTrue();
        assertThat(PENDING.isFulfilled()).isFalse();
        assertThat(REVIEW_REQUIRED.isFulfilled()).isFalse();
    }
}
