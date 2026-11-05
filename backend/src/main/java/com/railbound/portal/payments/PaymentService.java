package com.railbound.portal.payments;

import com.railbound.portal.audit.AuditService;
import com.railbound.portal.bundles.CoinBundle;
import com.railbound.portal.bundles.CoinBundleService;
import com.railbound.portal.common.DuplicateResourceException;
import com.railbound.portal.common.InvalidStateException;
import com.railbound.portal.common.ResourceNotFoundException;
import com.railbound.portal.refunds.RefundService;
import com.railbound.portal.wallet.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Order lifecycle and payment fulfillment.
 *
 * <p>INVARIANTS enforced here:
 * <ul>
 *   <li>Coins are credited only from a signature-verified provider notification (or an admin
 *       review decision), never from a browser redirect.</li>
 *   <li>Price, currency and coin amount come from the order snapshot, never from the client.</li>
 *   <li>A notification is honoured only if its amount AND currency equal the order's; otherwise the
 *       order is parked as REVIEW_REQUIRED and nothing is credited.</li>
 *   <li>One order is credited at most once: event de-duplication, the order state machine and the
 *       unique ledger reference all independently prevent a double credit.</li>
 * </ul>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public static final String LEDGER_REF_TYPE = "PAYMENT_ORDER";
    private static final Duration ORDER_TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentOrderRepository orderRepository;
    private final PaymentEventRepository eventRepository;
    private final CoinBundleService bundleService;
    private final WalletService walletService;
    private final RefundService refundService;
    private final AuditService auditService;
    private final MockPaymentAdapter mockPaymentAdapter;
    private final PayHereAdapter payHereAdapter;
    private final String defaultProvider;

    public PaymentService(PaymentOrderRepository orderRepository,
                          PaymentEventRepository eventRepository,
                          CoinBundleService bundleService,
                          WalletService walletService,
                          RefundService refundService,
                          AuditService auditService,
                          MockPaymentAdapter mockPaymentAdapter,
                          PayHereAdapter payHereAdapter,
                          @Value("${app.payment.default-provider:mock}") String defaultProvider) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.bundleService = bundleService;
        this.walletService = walletService;
        this.refundService = refundService;
        this.auditService = auditService;
        this.mockPaymentAdapter = mockPaymentAdapter;
        this.payHereAdapter = payHereAdapter;
        this.defaultProvider = defaultProvider;
    }

    // ------------------------------------------------------------------ providers

    /** Providers that are currently usable, real provider first. */
    public List<Map<String, Object>> listProviders() {
        List<Map<String, Object>> providers = new ArrayList<>();
        if (payHereAdapter.isEnabled()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", PayHereAdapter.PROVIDER);
            p.put("label", "PayHere (cards, wallets, bank)");
            p.put("sandbox", payHereAdapter.isSandbox());
            providers.add(p);
        }
        if (mockPaymentAdapter.isEnabled()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", MockPaymentAdapter.PROVIDER);
            p.put("label", "Mock checkout (development only)");
            p.put("sandbox", true);
            providers.add(p);
        }
        return providers;
    }

    private PaymentGatewayAdapter getAdapter(String providerName) {
        String name = providerName == null ? "" : providerName.trim().toLowerCase(Locale.ROOT);
        PaymentGatewayAdapter adapter = null;
        if (MockPaymentAdapter.PROVIDER.equals(name)) {
            adapter = mockPaymentAdapter;
        } else if (PayHereAdapter.PROVIDER.equals(name)) {
            adapter = payHereAdapter;
        }
        if (adapter == null || !adapter.isEnabled()) {
            throw new ResourceNotFoundException("Unsupported or disabled payment provider: " + providerName);
        }
        return adapter;
    }

    // ------------------------------------------------------------------ checkout

    /** Backwards compatible entry point: creates the order and returns it. */
    @Transactional
    public PaymentOrder createCheckoutSession(String userId, String bundleSku, String provider, String idempotencyKey) {
        return startCheckout(userId, bundleSku, provider, idempotencyKey).order();
    }

    /**
     * Creates (or, for a repeated Idempotency-Key, re-opens) an order for a bundle and returns where
     * the player should go to pay. Price and coins are snapshotted from the trusted bundle config.
     */
    @Transactional
    public CheckoutStart startCheckout(String userId, String bundleSku, String provider, String idempotencyKey) {
        String selected = (provider != null && !provider.isBlank()) ? provider : defaultProvider;
        PaymentGatewayAdapter adapter = getAdapter(selected);

        PaymentOrder order = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            order = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
        }

        if (order != null) {
            if (!adapter.getProviderName().equals(order.getProvider())
                    || (bundleSku != null && !bundleSku.equals(order.getBundleSku()))) {
                throw new DuplicateResourceException("Idempotency-Key was already used for a different checkout request");
            }
            log.info("Idempotent checkout replay for user {} order {}", userId, order.getOrderId());
        } else {
            if (bundleSku == null || bundleSku.isBlank()) {
                throw new InvalidStateException("bundleSku is required");
            }
            CoinBundle bundle = bundleService.getBundleBySku(bundleSku);
            if (!bundle.isActive()) {
                throw new InvalidStateException("This coin bundle is not available");
            }
            String orderId = "ord_" + UUID.randomUUID().toString().replace("-", "");
            order = new PaymentOrder(
                    orderId, userId, bundle.getSku(), bundle.getTotalCoins(),
                    bundle.getFiatAmountMinor(), bundle.getCurrency(),
                    adapter.getProviderName(), idempotencyKey,
                    Instant.now().plus(ORDER_TTL));
            log.info("Created order {} for user {} bundle {} via {}", orderId, userId, bundle.getSku(), adapter.getProviderName());
        }

        boolean payable = (order.getStatus() == PaymentOrderStatus.PENDING || order.getStatus() == PaymentOrderStatus.CREATED)
                && order.getExpiresAt() != null && order.getExpiresAt().isAfter(Instant.now());
        if (!payable) {
            return new CheckoutStart(orderRepository.save(order), null);
        }

        // A fresh one-time token on every call; only its hash is stored.
        String token = newCheckoutToken();
        order.setCheckoutTokenHash(sha256Hex(token));
        PaymentGatewayAdapter.CheckoutSessionResult session = adapter.createCheckoutSession(order, token);
        order.setProviderSessionId(session.providerSessionId());
        order = orderRepository.save(order);
        return new CheckoutStart(order, session.checkoutUrl());
    }

    /**
     * Returns the signed form fields for PayHere's hosted page. Authorized by the one-time checkout
     * token that was issued to the order owner, so it can be called from a browser tab or from the
     * Unity client's external browser without a session.
     */
    @Transactional
    public HostedForm buildHostedForm(String orderId, String checkoutToken, BillingDetails billing) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (checkoutToken == null || order.getCheckoutTokenHash() == null
                || !MessageDigest.isEqual(
                        sha256Hex(checkoutToken).getBytes(StandardCharsets.UTF_8),
                        order.getCheckoutTokenHash().getBytes(StandardCharsets.UTF_8))) {
            throw new AccessDeniedException("Invalid or expired checkout link");
        }
        if (!PayHereAdapter.PROVIDER.equals(order.getProvider())) {
            throw new InvalidStateException("This order does not use the PayHere checkout");
        }
        if (order.getStatus() != PaymentOrderStatus.PENDING && order.getStatus() != PaymentOrderStatus.CREATED) {
            throw new InvalidStateException("This order is not awaiting payment (status: " + order.getStatus() + ")");
        }
        if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidStateException("This checkout session has expired. Please start a new top-up.");
        }
        if (billing == null) {
            throw new InvalidStateException("Billing details are required");
        }

        HostedForm form = payHereAdapter.buildHostedForm(order, billing.validated());
        if (order.getStatus() == PaymentOrderStatus.CREATED) {
            transition(order, PaymentOrderStatus.PENDING);
            orderRepository.save(order);
        }
        return form;
    }

    // ------------------------------------------------------------------ webhooks

    /**
     * Entry point for provider notifications. Returns true when the notification was accepted
     * (including harmless duplicates); throws InvalidSignatureException for forgeries.
     */
    @Transactional
    public boolean processWebhook(String providerName, String rawBody, String signatureHeader) {
        PaymentGatewayAdapter adapter = getAdapter(providerName);
        WebhookEvent event = adapter.verifyAndParseWebhook(rawBody, signatureHeader);
        return applyEvent(adapter.getProviderName(), event);
    }

    private boolean applyEvent(String provider, WebhookEvent event) {
        if (eventRepository.existsByProviderAndProviderEventId(provider, event.eventId())) {
            log.info("Duplicate {} event {} ignored", provider, event.eventId());
            return true;
        }

        Optional<PaymentOrder> maybeOrder = orderRepository.findByOrderId(event.orderId());
        if (maybeOrder.isEmpty()) {
            log.warn("Verified {} event {} references unknown order {}", provider, event.eventId(), event.orderId());
            eventRepository.save(new PaymentEvent(provider, event.eventId(), event.orderId(),
                    "ORDER_NOT_FOUND", event.sanitizedPayload()));
            return true;
        }

        PaymentOrder order = maybeOrder.get();
        if (!provider.equalsIgnoreCase(order.getProvider())) {
            log.error("Event from {} for order {} that belongs to provider {}", provider, order.getOrderId(), order.getProvider());
            throw new InvalidStateException("Notification provider does not match the order's provider");
        }

        String result;
        switch (event.outcome()) {
            case SUCCEEDED -> result = applySuccess(provider, order, event);
            case PENDING -> result = applyPending(order);
            case CANCELLED -> result = applyUnpaidEnd(order, PaymentOrderStatus.CANCELLED, event);
            case FAILED -> result = applyUnpaidEnd(order, PaymentOrderStatus.FAILED, event);
            case CHARGED_BACK -> result = applyChargeback(order);
            default -> throw new InvalidStateException("Unsupported payment event outcome");
        }

        eventRepository.save(new PaymentEvent(provider, event.eventId(), order.getOrderId(), result, event.sanitizedPayload()));
        return true;
    }

    private String applySuccess(String provider, PaymentOrder order, WebhookEvent event) {
        PaymentOrderStatus previous = order.getStatus();
        if (previous.isFulfilled()) {
            log.info("Success event for already fulfilled order {} ignored", order.getOrderId());
            return "DUPLICATE_IGNORED";
        }

        boolean amountOk = event.amountMinor() != null && event.amountMinor() == order.getAmountMinor();
        boolean currencyOk = event.currency() != null && event.currency().equalsIgnoreCase(order.getCurrency());
        if (!amountOk || !currencyOk) {
            log.error("Payment mismatch on order {}: expected {} {} minor, provider reported {} {}",
                    order.getOrderId(), order.getAmountMinor(), order.getCurrency(), event.amountMinor(), event.currency());
            if (previous != PaymentOrderStatus.REVIEW_REQUIRED) {
                transition(order, PaymentOrderStatus.REVIEW_REQUIRED);
            }
            order.setProviderPaymentId(event.providerPaymentId());
            order.setStatusMessage("Amount/currency mismatch: expected " + order.getAmountMinor() + " "
                    + order.getCurrency() + " (minor units), provider reported " + event.amountMinor() + " " + event.currency());
            orderRepository.save(order);
            auditService.record("SYSTEM:" + provider, "PAYMENT_MISMATCH", order.getOrderId(), "REVIEW_REQUIRED",
                    order.getStatusMessage());
            return "AMOUNT_MISMATCH";
        }

        creditOrder(order, event.providerPaymentId());
        auditService.record("SYSTEM:" + provider, "PAYMENT_COMPLETED", order.getOrderId(), "SUCCESS",
                "coins=" + order.getCoinsToCredit() + " paymentId=" + event.providerPaymentId());

        boolean late = previous == PaymentOrderStatus.EXPIRED || previous == PaymentOrderStatus.CANCELLED
                || previous == PaymentOrderStatus.FAILED;
        return late ? "PROCESSED_LATE" : "PROCESSED";
    }

    private String applyPending(PaymentOrder order) {
        if (order.getStatus() == PaymentOrderStatus.CREATED || order.getStatus() == PaymentOrderStatus.PENDING) {
            transition(order, PaymentOrderStatus.PROCESSING);
            orderRepository.save(order);
            return "PROCESSED";
        }
        return "IGNORED_OUT_OF_ORDER";
    }

    private String applyUnpaidEnd(PaymentOrder order, PaymentOrderStatus target, WebhookEvent event) {
        PaymentOrderStatus s = order.getStatus();
        if (s == PaymentOrderStatus.CREATED || s == PaymentOrderStatus.PENDING || s == PaymentOrderStatus.PROCESSING) {
            transition(order, target);
            order.setStatusMessage(event.statusMessage());
            orderRepository.save(order);
            return "PROCESSED";
        }
        return "IGNORED_OUT_OF_ORDER";
    }

    private String applyChargeback(PaymentOrder order) {
        if (order.getStatus() == PaymentOrderStatus.COMPLETED) {
            transition(order, PaymentOrderStatus.CHARGED_BACK);
            orderRepository.save(order);
            refundService.openChargebackCase(order);
            return "PROCESSED";
        }
        if (order.getStatus().isFulfilled()) {
            return "IGNORED_OUT_OF_ORDER";
        }
        // A chargeback for something we never fulfilled is unexpected: a human must look at it.
        if (order.getStatus() != PaymentOrderStatus.REVIEW_REQUIRED) {
            transition(order, PaymentOrderStatus.REVIEW_REQUIRED);
        }
        order.setStatusMessage("Chargeback reported for an order that was never fulfilled");
        orderRepository.save(order);
        auditService.record("SYSTEM", "CHARGEBACK_UNFULFILLED", order.getOrderId(), "REVIEW_REQUIRED", order.getStatusMessage());
        return "REVIEW";
    }

    /** Marks the order COMPLETED and credits the wallet exactly once. */
    private void creditOrder(PaymentOrder order, String providerPaymentId) {
        transition(order, PaymentOrderStatus.COMPLETED);
        order.setCompletedAt(Instant.now());
        order.setStatusMessage(null);
        if (providerPaymentId != null) {
            order.setProviderPaymentId(providerPaymentId);
        }
        orderRepository.save(order);

        if (walletService.hasLedgerReference(LEDGER_REF_TYPE, order.getOrderId())) {
            log.error("Order {} had no COMPLETED status but a ledger credit already exists; not crediting again",
                    order.getOrderId());
            return;
        }
        walletService.creditRealCoins(order.getUserId(), order.getCoinsToCredit(), LEDGER_REF_TYPE,
                order.getOrderId(), "Top-up via " + order.getProvider() + " (" + order.getBundleSku() + ")");
    }

    private void transition(PaymentOrder order, PaymentOrderStatus next) {
        if (order.getStatus() == next) {
            return;
        }
        if (!order.getStatus().canTransitionTo(next)) {
            throw new InvalidStateException("Illegal payment order transition " + order.getStatus() + " -> " + next);
        }
        order.setStatus(next);
    }

    // ------------------------------------------------------------------ queries

    public PaymentOrder getOrderForUser(String orderId, String userId) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.getUserId().equals(userId)) {
            throw new AccessDeniedException("You do not have access to this order");
        }
        return order;
    }

    public List<PaymentOrder> getOrdersForUser(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<PaymentOrder> getAllOrdersForAdmin() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<PaymentOrder> getOrdersAwaitingReview() {
        return orderRepository.findByStatusOrderByCreatedAtDesc(PaymentOrderStatus.REVIEW_REQUIRED);
    }

    // ------------------------------------------------------------------ housekeeping / admin

    /** Expires unpaid orders whose local checkout window has passed. Called by the scheduler. */
    @Transactional
    public int expireStaleOrders() {
        List<PaymentOrder> stale = orderRepository.findByStatusInAndExpiresAtBefore(
                List.of(PaymentOrderStatus.CREATED, PaymentOrderStatus.PENDING), Instant.now());
        for (PaymentOrder order : stale) {
            transition(order, PaymentOrderStatus.EXPIRED);
            order.setStatusMessage("Checkout session expired before a verified payment notification arrived");
            orderRepository.save(order);
        }
        if (!stale.isEmpty()) {
            log.info("Expired {} stale unpaid order(s)", stale.size());
        }
        return stale.size();
    }

    /** Consistency check for one order: status vs. ledger. Also expires it if it is stale. */
    @Transactional
    public Map<String, Object> reconcileOrder(String orderId, String actorUserId) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        boolean expiredNow = false;
        if ((order.getStatus() == PaymentOrderStatus.CREATED || order.getStatus() == PaymentOrderStatus.PENDING)
                && order.getExpiresAt() != null && order.getExpiresAt().isBefore(Instant.now())) {
            transition(order, PaymentOrderStatus.EXPIRED);
            order.setStatusMessage("Checkout session expired before a verified payment notification arrived");
            orderRepository.save(order);
            expiredNow = true;
        }

        boolean credited = walletService.hasLedgerReference(LEDGER_REF_TYPE, orderId);
        boolean consistent = order.getStatus().isFulfilled() == credited;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("status", order.getStatus().name());
        result.put("ledgerCreditPresent", credited);
        result.put("consistent", consistent);
        result.put("expiredNow", expiredNow);
        result.put("note", consistent
                ? "Order status and ledger agree."
                : "INCONSISTENT: investigate before changing anything. Confirm the payment in the PayHere dashboard.");

        auditService.record(actorUserId, "ORDER_RECONCILED", orderId, consistent ? "CONSISTENT" : "INCONSISTENT",
                "status=" + order.getStatus() + " ledgerCredit=" + credited);
        return result;
    }

    /**
     * Resolves a REVIEW_REQUIRED order after a human verified the payment in the PayHere dashboard:
     * approve credits the coins once, reject marks the order FAILED.
     */
    @Transactional
    public PaymentOrder resolveReview(String orderId, boolean approve, String note, String actorUserId) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (order.getStatus() != PaymentOrderStatus.REVIEW_REQUIRED) {
            throw new InvalidStateException("Order is not awaiting review (status: " + order.getStatus() + ")");
        }
        if (note == null || note.isBlank()) {
            throw new InvalidStateException("A review note is required");
        }

        if (approve) {
            creditOrder(order, order.getProviderPaymentId());
        } else {
            transition(order, PaymentOrderStatus.FAILED);
            order.setStatusMessage("Rejected after review: " + note.trim());
            orderRepository.save(order);
        }
        auditService.record(actorUserId, approve ? "REVIEW_APPROVED" : "REVIEW_REJECTED", orderId,
                approve ? "COMPLETED" : "FAILED", note.trim());
        return order;
    }

    // ------------------------------------------------------------------ mock provider (dev only)

    /**
     * Simulates the provider completing a hosted checkout. Development only: disabled unless
     * {@code app.payment.mock.enabled=true}, and only the order's owner may trigger it. It goes
     * through the same signed-webhook path as a real provider.
     */
    @Transactional
    public boolean mockCompleteOrder(String orderId, String userId) {
        if (!mockPaymentAdapter.isEnabled()) {
            throw new ResourceNotFoundException("Mock payment provider is disabled");
        }
        PaymentOrder order = getOrderForUser(orderId, userId);
        if (!MockPaymentAdapter.PROVIDER.equals(order.getProvider())) {
            throw new InvalidStateException("This order was not created with the mock provider");
        }

        String eventId = "evt_mock_" + UUID.randomUUID().toString().replace("-", "");
        String payload = String.format(
                "{\"type\":\"payment.succeeded\",\"eventId\":\"%s\",\"orderId\":\"%s\",\"amountMinor\":%d,\"currency\":\"%s\"}",
                eventId, order.getOrderId(), order.getAmountMinor(), order.getCurrency());

        try {
            String signature = mockPaymentAdapter.computeHmacSha256(payload, mockPaymentAdapter.getSecret());
            return processWebhook(MockPaymentAdapter.PROVIDER, payload, signature);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Mock webhook signing failed", e);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String newCheckoutToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
