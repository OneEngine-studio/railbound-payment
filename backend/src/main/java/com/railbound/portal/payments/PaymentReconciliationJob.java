package com.railbound.portal.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically expires unpaid orders whose checkout window has passed. */
@Component
public class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);

    private final PaymentService paymentService;

    public PaymentReconciliationJob(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelayString = "${app.payment.reconcile-interval-ms:60000}", initialDelayString = "${app.payment.reconcile-interval-ms:60000}")
    public void expireStaleOrders() {
        try {
            paymentService.expireStaleOrders();
        } catch (RuntimeException e) {
            log.error("Stale order expiry run failed; will retry on the next cycle", e);
        }
    }
}
