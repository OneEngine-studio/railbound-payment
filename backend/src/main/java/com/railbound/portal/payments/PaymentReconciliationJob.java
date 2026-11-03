package com.railbound.portal.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Periodic safety net for money that may have moved without a callback.
 *
 * <p>Two failure modes are covered:</p>
 * <ol>
 *   <li><strong>Paid but unfulfilled</strong> — the provider charged the card but
 *       the webhook was lost. The order is re-read from PayHere's Retrieval API
 *       and fulfilled through the same verified path as a webhook.</li>
 *   <li><strong>Stale checkout</strong> — a PENDING order older than its expiry
 *       is closed as EXPIRED so it stops showing as payable.</li>
 * </ol>
 *
 * <p>Disabled by default in tests; enable with
 * {@code app.payments.reconciliation.enabled=true}.</p>
 */
@Component
public class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);

    private final PaymentOrderRepository orderRepository;
    private final PaymentService paymentService;
    private final boolean enabled;

    public PaymentReconciliationJob(
            PaymentOrderRepository orderRepository,
            PaymentService paymentService,
            @Value("${app.payments.reconciliation.enabled:true}") boolean enabled) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.enabled = enabled;
    }

    @Scheduled(
            initialDelayString = "${app.payments.reconciliation.initial-delay-ms:60000}",
            fixedDelayString = "${app.payments.reconciliation.interval-ms:900000}")
    public void scheduledReconciliation() {
        if (!enabled) {
            return;
        }
        try {
            runOnce();
        } catch (Exception e) {
            log.error("Payment reconciliation iteration failed", e);
        }
    }

    /** Returns the number of orders that were reconciled or expired. */
    public int runOnce() {
        int changed = 0;
        Instant now = Instant.now();

        // 1. Close checkouts that were never paid and are past their expiry.
        List<PaymentOrder> stalePending = orderRepository
                .findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        PaymentOrderStatus.PENDING, now.minus(45, ChronoUnit.MINUTES));
        for (PaymentOrder order : stalePending) {
            if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(now)) {
                order.setStatus(PaymentOrderStatus.EXPIRED);
                orderRepository.save(order);
                changed++;
                log.info("Expired stale checkout order {}", order.getOrderId());
            }
        }

        // 2. Try to recover paid-but-unfulfilled orders through the provider API.
        List<PaymentOrder> pendingProcessing = orderRepository
                .findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        PaymentOrderStatus.PROCESSING, now.minus(10, ChronoUnit.MINUTES));
        for (PaymentOrder order : pendingProcessing) {
            if (!"payhere".equalsIgnoreCase(order.getProvider())) {
                continue;
            }
            try {
                PaymentService.ReconciliationResult result = paymentService.reconcileOrder(order.getOrderId());
                if (result.order().getStatus() == PaymentOrderStatus.COMPLETED) {
                    changed++;
                    log.info("Reconciliation recovered paid order {} ({})",
                            order.getOrderId(), result.providerStatus());
                }
            } catch (Exception e) {
                // Merchant API may be unconfigured or temporarily down; keep the
                // order in PROCESSING so the operator can retry later.
                log.warn("Reconciliation skipped order {}: {}", order.getOrderId(), e.getMessage());
            }
        }

        return changed;
    }
}
