package com.railbound.portal.refunds;

import com.railbound.portal.audit.AuditService;
import com.railbound.portal.common.DuplicateResourceException;
import com.railbound.portal.common.InvalidStateException;
import com.railbound.portal.common.ResourceNotFoundException;
import com.railbound.portal.ledger.LedgerEntryType;
import com.railbound.portal.payments.PaymentOrder;
import com.railbound.portal.payments.PaymentOrderRepository;
import com.railbound.portal.payments.PaymentOrderStatus;
import com.railbound.portal.wallet.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Refund and chargeback handling.
 *
 * <p>POLICY (matches the project rules): a reversal NEVER creates a negative coin balance. If the
 * player has already spent the coins, the case is parked as NEEDS_REVIEW for a human decision
 * (for example revoking the purchased items) instead of silently creating coin debt. Each
 * successful reversal appends a REFUND ledger entry with a negative delta, so the wallet/ledger
 * invariant is preserved.
 *
 * <p>The actual money refund for a REFUND case is performed by finance staff in the PayHere
 * merchant dashboard (this service does not call a provider refund API); the staff member then
 * confirms it with {@link #markProviderRefunded}.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    public static final String LEDGER_REF_TYPE = "REFUND_CASE";

    private final RefundCaseRepository caseRepository;
    private final PaymentOrderRepository orderRepository;
    private final WalletService walletService;
    private final AuditService auditService;

    public RefundService(RefundCaseRepository caseRepository,
                         PaymentOrderRepository orderRepository,
                         WalletService walletService,
                         AuditService auditService) {
        this.caseRepository = caseRepository;
        this.orderRepository = orderRepository;
        this.walletService = walletService;
        this.auditService = auditService;
    }

    public List<RefundCase> listCases() {
        return caseRepository.findAllByOrderByCreatedAtDesc();
    }

    /** Finance-initiated refund of a COMPLETED order. */
    @Transactional
    public RefundCase createRefund(String orderId, String reason, String actorUserId) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidStateException("A refund reason is required");
        }
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != PaymentOrderStatus.COMPLETED) {
            throw new InvalidStateException("Only COMPLETED orders can be refunded (current status: " + order.getStatus() + ")");
        }
        if (caseRepository.findByOrderId(orderId).isPresent()) {
            throw new DuplicateResourceException("A refund case already exists for order " + orderId);
        }

        order.setStatus(PaymentOrderStatus.REFUND_PENDING);
        orderRepository.save(order);

        RefundCase refundCase = newCase(order, RefundType.REFUND, reason.trim(), actorUserId);
        attemptReversal(refundCase);
        refundCase = caseRepository.save(refundCase);

        auditService.record(actorUserId, "REFUND_CREATED", orderId, refundCase.getStatus().name(),
                "refundId=" + refundCase.getRefundId() + " coins=" + refundCase.getCoinsToReverse());
        return refundCase;
    }

    /**
     * Opens a chargeback case from a verified provider notification. The money has already been
     * taken back by the card network, so the only open question is recovering the coins.
     */
    @Transactional
    public RefundCase openChargebackCase(PaymentOrder order) {
        RefundCase existing = caseRepository.findByOrderId(order.getOrderId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        RefundCase refundCase = newCase(order, RefundType.CHARGEBACK,
                "Chargeback reported by payment provider", "SYSTEM");
        attemptReversal(refundCase);
        refundCase = caseRepository.save(refundCase);

        auditService.record("SYSTEM", "CHARGEBACK_RECEIVED", order.getOrderId(), refundCase.getStatus().name(),
                "refundId=" + refundCase.getRefundId());
        return refundCase;
    }

    /** Retries the coin reversal for a case that was parked because the player had spent the coins. */
    @Transactional
    public RefundCase retryReversal(String refundId, String actorUserId) {
        RefundCase refundCase = find(refundId);
        if (refundCase.getStatus() != RefundStatus.NEEDS_REVIEW) {
            throw new InvalidStateException("Only NEEDS_REVIEW cases can be retried (current status: " + refundCase.getStatus() + ")");
        }
        attemptReversal(refundCase);
        refundCase = caseRepository.save(refundCase);
        auditService.record(actorUserId, "REFUND_REVERSAL_RETRIED", refundCase.getOrderId(),
                refundCase.getStatus().name(), "refundId=" + refundId);
        return refundCase;
    }

    /**
     * Confirms that the money was returned to the customer through the provider (e.g. the PayHere
     * dashboard). Requires the coins to have been reversed first.
     */
    @Transactional
    public RefundCase markProviderRefunded(String refundId, String providerRefundId, String actorUserId) {
        RefundCase refundCase = find(refundId);
        if (refundCase.getType() != RefundType.REFUND) {
            throw new InvalidStateException("Chargebacks are settled by the card network; nothing to confirm");
        }
        if (refundCase.getStatus() != RefundStatus.COINS_REVERSED) {
            throw new InvalidStateException("Coins must be reversed before the refund can be confirmed (current status: "
                    + refundCase.getStatus() + ")");
        }

        PaymentOrder order = orderRepository.findByOrderId(refundCase.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + refundCase.getOrderId()));
        if (!order.getStatus().canTransitionTo(PaymentOrderStatus.REFUNDED)) {
            throw new InvalidStateException("Order cannot move to REFUNDED from " + order.getStatus());
        }
        order.setStatus(PaymentOrderStatus.REFUNDED);
        orderRepository.save(order);

        refundCase.setStatus(RefundStatus.COMPLETED);
        refundCase.setProviderRefundId(providerRefundId == null || providerRefundId.isBlank() ? null : providerRefundId.trim());
        refundCase.setUpdatedAt(Instant.now());
        refundCase = caseRepository.save(refundCase);

        auditService.record(actorUserId, "REFUND_CONFIRMED", refundCase.getOrderId(), "COMPLETED",
                "refundId=" + refundId);
        return refundCase;
    }

    private RefundCase find(String refundId) {
        return caseRepository.findByRefundId(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund case not found: " + refundId));
    }

    private RefundCase newCase(PaymentOrder order, RefundType type, String reason, String actor) {
        RefundCase refundCase = new RefundCase();
        refundCase.setRefundId("rfd_" + UUID.randomUUID().toString().replace("-", ""));
        refundCase.setOrderId(order.getOrderId());
        refundCase.setUserId(order.getUserId());
        refundCase.setType(type);
        refundCase.setAmountMinor(order.getAmountMinor());
        refundCase.setCurrency(order.getCurrency());
        refundCase.setCoinsToReverse(order.getCoinsToCredit());
        refundCase.setReason(reason);
        refundCase.setCreatedBy(actor);
        return refundCase;
    }

    /** Removes the coins if (and only if) the player still holds all of them. */
    private void attemptReversal(RefundCase refundCase) {
        String description = refundCase.getType() + " of order " + refundCase.getOrderId();
        boolean reversed = walletService.tryDebitRealCoins(
                refundCase.getUserId(), refundCase.getCoinsToReverse(), LedgerEntryType.REFUND,
                LEDGER_REF_TYPE, refundCase.getRefundId(), description);

        if (reversed) {
            refundCase.setCoinsReversed(refundCase.getCoinsToReverse());
            refundCase.setStatus(refundCase.getType() == RefundType.CHARGEBACK
                    ? RefundStatus.COMPLETED : RefundStatus.COINS_REVERSED);
            refundCase.setResolutionNote("Coins removed from the player's wallet.");
        } else {
            refundCase.setStatus(RefundStatus.NEEDS_REVIEW);
            refundCase.setResolutionNote("Player no longer holds the full coin amount (likely already spent). "
                    + "Review the player's recent purchases and decide on item revocation, then retry.");
            log.warn("Refund case {} parked for review: insufficient balance to reverse {} coins",
                    refundCase.getRefundId(), refundCase.getCoinsToReverse());
        }
        refundCase.setUpdatedAt(Instant.now());
    }
}
