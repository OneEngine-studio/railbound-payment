package com.railbound.portal.refunds;

import com.railbound.portal.common.DuplicateResourceException;
import com.railbound.portal.common.InvalidStateException;
import com.railbound.portal.common.PaymentProviderException;
import com.railbound.portal.common.ResourceNotFoundException;
import com.railbound.portal.audit.AuditService;
import com.railbound.portal.ledger.LedgerEntryType;
import com.railbound.portal.outbox.OutboxService;
import com.railbound.portal.payments.PayHereMerchantApiClient;
import com.railbound.portal.payments.PaymentOrder;
import com.railbound.portal.payments.PaymentOrderRepository;
import com.railbound.portal.payments.PaymentOrderStatus;
import com.railbound.portal.wallet.Wallet;
import com.railbound.portal.wallet.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controlled fiat refund + Real Coin reversal workflow.
 *
 * <p>Order of operations (intentional):</p>
 * <ol>
 *   <li>Persist an auditable {@link RefundCase} in REQUESTED state.</li>
 *   <li>Call PayHere's Refund API <strong>outside</strong> any DB transaction.</li>
 *   <li>In one MongoDB transaction, record the provider refund and reverse coins
 *       only if the wallet still holds enough coins.</li>
 * </ol>
 *
 * <p>If the player already spent the coins, the case is left in
 * REVIEW_REQUIRED. The wallet never goes negative and support can decide on
 * entitlement revocation or a different compensation path.</p>
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final PaymentOrderRepository orderRepository;
    private final RefundCaseRepository refundRepository;
    private final PayHereMerchantApiClient payHereClient;
    private final WalletService walletService;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public RefundService(
            PaymentOrderRepository orderRepository,
            RefundCaseRepository refundRepository,
            PayHereMerchantApiClient payHereClient,
            WalletService walletService,
            OutboxService outboxService,
            AuditService auditService,
            PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
        this.payHereClient = payHereClient;
        this.walletService = walletService;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<RefundCase> getAllRefundCases() {
        return refundRepository.findAllByOrderByCreatedAtDesc();
    }

    public RefundCase requestRefund(String orderId, String reason, String actorUserId) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidStateException("A refund reason is required for the audit trail");
        }

        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!PaymentOrderStatus.COMPLETED.equals(order.getStatus())
                && !PaymentOrderStatus.REFUND_PENDING.equals(order.getStatus())) {
            throw new InvalidStateException(
                    "Only completed top-up orders can be refunded (current status: " + order.getStatus() + ")");
        }
        if (!"payhere".equalsIgnoreCase(order.getProvider())) {
            throw new InvalidStateException("Automated refunds are only implemented for payhere orders");
        }

        RefundCase refund = getOrCreateCase(order, reason, actorUserId);
        if (!RefundCase.STATUS_FAILED.equals(refund.getStatus())) {
            // Idempotent: an in-flight or completed case is returned as-is.
            log.info("Refund case {} already exists for order {} with status {}",
                    refund.getRefundCaseId(), orderId, refund.getStatus());
            return refund;
        }

        String paymentId = resolvePaymentId(order);
        refund.setProviderPaymentId(paymentId);
        refundRepository.save(refund);

        PayHereMerchantApiClient.PayHereRefundResult result;
        try {
            result = payHereClient.refund(paymentId, null, reason);
        } catch (RuntimeException e) {
            markFailed(refund, e.getMessage());
            throw e;
        }
        if (!result.success()) {
            markFailed(refund, result.message());
            throw new PaymentProviderException("PayHere refund was rejected: " + result.message());
        }

        final String refundId = refund.getRefundCaseId();
        try {
            transactionTemplate.executeWithoutResult(status -> applyRefundResult(refundId, result));
        } catch (RuntimeException e) {
            // The provider has the money request; leave the case in REQUESTED so
            // reconciliation/support can see the ambiguity instead of retrying.
            log.error("Provider refund succeeded but DB reversal failed for order {}. " +
                    "Manual reconciliation required.", orderId, e);
            throw e;
        }

        RefundCase completed = refundRepository.findByRefundCaseId(refundId).orElse(refund);
        auditService.recordWithActor(actorUserId, "FINANCE_SUPPORT", "REFUND_" + completed.getStatus(),
                "refund_case", refundId,
                Map.of("orderId", orderId),
                Map.of("status", completed.getStatus(),
                        "coinsReversed", completed.getCoinsReversed(),
                        "providerRefundId", String.valueOf(completed.getProviderRefundId())));
        return completed;
    }

    private RefundCase getOrCreateCase(PaymentOrder order, String reason, String actorUserId) {
        return refundRepository.findByOrderId(order.getOrderId())
                .map(existing -> {
                    if (RefundCase.STATUS_FAILED.equals(existing.getStatus())) {
                        existing.setStatus(RefundCase.STATUS_REQUESTED);
                        existing.setProviderMessage(null);
                        existing.setReason(reason);
                        existing.setCreatedBy(actorUserId);
                        existing.setUpdatedAt(Instant.now());
                        return refundRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    RefundCase c = new RefundCase();
                    c.setRefundCaseId("rfc_" + UUID.randomUUID().toString().replace("-", ""));
                    c.setOrderId(order.getOrderId());
                    c.setUserId(order.getUserId());
                    c.setProvider(order.getProvider());
                    c.setAmountMinor(order.getAmountMinor());
                    c.setCurrency(order.getCurrency());
                    c.setCoinsToReverse(order.getCoinsToCredit());
                    c.setStatus(RefundCase.STATUS_REQUESTED);
                    c.setReason(reason);
                    c.setCreatedBy(actorUserId);
                    try {
                        return refundRepository.save(c);
                    } catch (DuplicateKeyException e) {
                        // A concurrent operator won; use their case instead of
                        // issuing a second provider refund.
                        return refundRepository.findByOrderId(order.getOrderId())
                                .orElseThrow(() -> new DuplicateResourceException(
                                        "Refund case already exists for order " + order.getOrderId()));
                    }
                });
    }

    private String resolvePaymentId(PaymentOrder order) {
        if (order.getProviderPaymentId() != null && !order.getProviderPaymentId().isBlank()) {
            return order.getProviderPaymentId();
        }
        // Older orders may not have stored payment_id; retrieve it securely
        // through the merchant API rather than trusting a client value.
        return payHereClient.retrievePayment(order.getOrderId())
                .map(PayHereMerchantApiClient.PayHerePaymentRecord::paymentId)
                .filter(id -> id != null && !id.isBlank())
                .orElseThrow(() -> new InvalidStateException(
                        "No PayHere payment_id found for order " + order.getOrderId()));
    }

    private void applyRefundResult(String refundCaseId, PayHereMerchantApiClient.PayHereRefundResult result) {
        RefundCase refund = refundRepository.findByRefundCaseId(refundCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund case disappeared: " + refundCaseId));
        PaymentOrder order = orderRepository.findByOrderId(refund.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order disappeared: " + refund.getOrderId()));

        if (order.getProviderPaymentId() != null && !order.getProviderPaymentId().isBlank()) {
            refund.setProviderPaymentId(order.getProviderPaymentId());
        }
        refund.setProviderRefundId(result.providerRefundId());
        refund.setProviderMessage(result.message());
        refund.setStatus(RefundCase.STATUS_PROVIDER_REFUNDED);

        long coins = order.getCoinsToCredit();
        Wallet wallet = walletService.getOrCreateWallet(order.getUserId());

        if (wallet.getRealCoins() >= coins) {
            walletService.debitRealCoins(
                    order.getUserId(),
                    coins,
                    LedgerEntryType.REFUND,
                    "REFUND",
                    order.getOrderId(),
                    "Real Coin reversal for refunded top-up " + order.getOrderId()
            );
            order.setStatus(PaymentOrderStatus.REFUNDED);
            order.setRefundedAt(Instant.now());
            refund.setCoinsReversed(coins);
            refund.setStatus(RefundCase.STATUS_COMPLETED);

            // Same transaction as the reversal: the player is told only about a
            // refund that actually removed the coins.
            outboxService.enqueue(
                    OutboxService.REFUND_COMPLETED,
                    "refund_case",
                    refundCaseId,
                    Map.of(
                            "userId", order.getUserId(),
                            "orderId", order.getOrderId(),
                            "coinsReversed", coins
                    )
            );
            log.info("Refund {} completed: reverse {} RC for user {}",
                    refundCaseId, coins, order.getUserId());
        } else {
            // Do not create negative balances. Support must review entitlements.
            order.setStatus(PaymentOrderStatus.REFUND_PENDING);
            refund.setCoinsReversed(0);
            refund.setStatus(RefundCase.STATUS_REVIEW_REQUIRED);
            log.warn("Refund {} provider-completed but coin reversal requires review: " +
                    "balance {} < {} RC for user {}", refundCaseId,
                    wallet.getRealCoins(), coins, order.getUserId());
        }

        orderRepository.save(order);
        refundRepository.save(refund);
    }

    private void markFailed(RefundCase refund, String message) {
        refund.setStatus(RefundCase.STATUS_FAILED);
        refund.setProviderMessage(message == null ? "Unknown provider error" : message);
        refundRepository.save(refund);
        log.warn("Refund case {} failed: {}", refund.getRefundCaseId(), message);
    }
}
