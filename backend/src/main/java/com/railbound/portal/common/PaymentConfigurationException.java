package com.railbound.portal.common;

/**
 * Raised when a payment provider is selected but credentials/feature flags are
 * missing. This is intentionally a 503 (not a silent mock fallback) so that a
 * production deployment cannot accidentally fulfil payments with a simulator.
 */
public class PaymentConfigurationException extends RuntimeException {

    public PaymentConfigurationException(String message) {
        super(message);
    }
}
