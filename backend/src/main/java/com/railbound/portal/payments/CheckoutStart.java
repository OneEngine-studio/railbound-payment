package com.railbound.portal.payments;

/**
 * Result of starting a checkout.
 *
 * @param order       the persisted order
 * @param checkoutUrl where the player should be sent next; null when the order can no longer be
 *                    paid (already completed, expired, ...)
 */
public record CheckoutStart(PaymentOrder order, String checkoutUrl) {}
