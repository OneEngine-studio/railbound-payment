package com.railbound.portal.common;

/**
 * Upstream gateway failure (timeout, invalid credentials, declined refund,
 * unexpected response). The message is safe to return to operators but must not
 * contain merchant secrets.
 */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message) {
        super(message);
    }

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
