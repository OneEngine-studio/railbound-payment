package com.railbound.portal.payments;

import com.railbound.portal.bundles.CoinBundle;
import com.railbound.portal.bundles.CoinBundleService;
import com.railbound.portal.common.DuplicateResourceException;
import com.railbound.portal.common.InvalidSignatureException;
import com.railbound.portal.common.InvalidStateException;
import com.railbound.portal.common.PaymentConfigurationException;
import com.railbound.portal.common.ResourceNotFoundException;
import com.railbound.portal.identity.User;
import com.railbound.portal.identity.UserRepository;
import com.railbound.portal.outbox.OutboxService;
import com.railbound.portal.wallet.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative top-up orchestration.
 *
 * <p>Security invariants:</p>
 * <ol>
 *   <li>The client may only send {@code bundleSku}, {@code provider} and an
 *       idempotency key. Prices and coin amounts always come from the bundle
 *       snapshot in MongoDB.</li>
 *   <li>A redirect never credits coins. Only a cryptographically verified
 *       provider callback (or an authenticated PayHere Merchant API lookup) can
 *       fulfil an order.</li>
 *   <li>Fulfilment is a single MongoDB transaction: order -> COMPLETED, one
 *       wallet credit, one immutable ledger entry, one deduplicated event.</li>
 *   <li>Callbacks are deduplicated by provider event id and by the ledger's
 *       unique reference key, so retries can never double-credit.</li>
 * </ol>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PaymentOrderRepository orderRepository;
    private final PaymentEventRepository eventRepository;
    private final CoinBundleService bundleService;
    private final WalletService walletService;
    private final UserRepository userRepository;
    private final PayHereMerchantApiClient payHereMerchantApiClient;
    private final OutboxService outboxService;
    private final Map<String, PaymentGatewayAdapter> adapters;
    private final TransactionTemplate transactionTemplate;
    private final String defaultProvider;
    private final String publicBaseUrl;
    private final String frontendBaseUrl;

    public PaymentService(
            PaymentOrderRepository orderRepository,
            PaymentEventRepository eventRepository,
            CoinBundleService bundleService,
            WalletService walletService,
            UserRepository userRepository,
            PayHereMerchantApiClient payHereMerchantApiClient,
            OutboxService outboxService,
            List<PaymentGatewayAdapter> adapterList,
            PlatformTransactionManager transactionManager,
            @Value("${app.payment.default-provider:payhere}") String defaultProvider,
            @Value("${app.payment.public-base-url:http://localhost:8080}") String publicBaseUrl,
            @Value("${app.payment.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.bundleService = bundleService;
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.payHereMerchantApiClient = payHereMerchantApiClient;
        this.outboxService = outboxService;
        this.adapters = new LinkedHashMap<>();
        for (PaymentGatewayAdapter adapter : adapterList) {
            adapters.put(adapter.getProviderName().toLowerCase(Locale.ROOT), adapter);
        }
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.defaultProvider = defaultProvider == null ? "payhere" : defaultProvider.trim().toLowerCase(Locale.ROOT);
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);

        if (this.publicBaseUrl.contains("localhost") || this.publicBaseUrl.contains("127.0.0.1")) {
            log.warn("app.payment.public-base-url is '{}'. PayHere cannot reach localhost; set a public HTTPS " +
                    "tunnel/domain for PAYMENT_PUBLIC_BASE_URL before taking real payments.", this.publicBaseUrl);
        }
    }

    public PaymentGatewayAdapter getAdapter(String providerName) {
        String key = providerName == null || providerName.isBlank()
                ? defaultProvider
                : providerName.trim().toLowerCase(Locale.ROOT);
        PaymentGatewayAdapter adapter = adapters.get(key);
        if (adapter == null) {
            throw new ResourceNotFoundException("Unsupported payment provider: " + providerName);
        }
        if (!adapter.isConfigured()) {
            throw new PaymentConfigurationException(
                    "Payment provider '" + key + "' is not configured for this deployment.");
        }
        return adapter;
    }

    // ---------------------------------------------------------------------
    // Checkout creation
    // ---------------------------------------------------------------------

    public PaymentOrder createCheckoutSession(String userId, String bundleSku, String provider, String idempotencyKey) {
        if (userId == null || userId.isBlank()) {
            throw new AccessDeniedException("Authenticated player is required");
        }

        // Retry-safe: replaying the same idempotency key returns the original
        // order (and the original hosted checkout URL), never a second charge.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<PaymentOrder> existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent checkout request detected for user {} and key {}", userId, idempotencyKey);
                return existing.get();
            }
        }

        CoinBundle bundle = bundleService.getBundleBySku(bundleSku);
        if (!bundle.isActive()) {
            throw new InvalidStateException("Bundle is not available for purchase: " + bundleSku);
        }

        String selectedProvider = (provider != null && !provider.isBlank()) ? provider : defaultProvider;
        PaymentGatewayAdapter adapter = getAdapter(selectedProvider);

        String orderId = "ord_" + UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);
        String checkoutToken = generateCheckoutToken();

        PaymentOrder order = new PaymentOrder(
                orderId,
                userId,
                bundle.getSku(),
                bundle.getTotalCoins(),
                bundle.getFiatAmountMinor(),
                bundle.getCurrency(),
                adapter.getProviderName(),
                idempotencyKey,
                expiresAt
        );
        order.setCheckoutToken(checkoutToken);

        User user = userRepository.findByUserId(userId).orElse(null);
        PaymentGatewayAdapter.CheckoutContext context = buildCheckoutContext(order, user);

        // The adapter is called before persistence so a missing PayHere
        // credential produces a clean 503 instead of a dangling PENDING order.
        PaymentGatewayAdapter.CheckoutSessionResult result = adapter.createCheckoutSession(order, context);
        if (result.providerSessionId() != null && !result.providerSessionId().isBlank()) {
            order.setProviderSessionId(result.providerSessionId());
        }

        String checkoutUrl = result.requiresFormPost()
                ? hostedBridgeUrl(orderId, checkoutToken)
                : result.checkoutUrl();
        order.setCheckoutUrl(checkoutUrl);

        PaymentOrder saved = orderRepository.save(order);
        log.info("Created {} top-up order {} for user {}: {} RC for {} (minor units)",
                saved.getProvider(), saved.getOrderId(), userId, saved.getCoinsToCredit(), saved.getAmountMinor());
        return saved;
    }

    public String checkoutUrlFor(PaymentOrder order) {
        return order.getCheckoutUrl();
    }

    public PaymentOrder getOrderForUser(String orderId, String userId) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!order.getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied: Order does not belong to authenticated player");
        }
        return order;
    }

    public List<PaymentOrder> getOrdersForUser(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<PaymentOrder> getAllOrdersForAdmin() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    // ---------------------------------------------------------------------
    // Hosted checkout bridge (PayHere requires an HTML form POST)
    // ---------------------------------------------------------------------

    public HostedCheckout prepareHostedCheckout(String orderId, String checkoutToken) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // Constant-time compare avoids turning the token into a timing oracle.
        if (checkoutToken == null || order.getCheckoutToken() == null
                || !PayHereAdapter.constantTimeEquals(order.getCheckoutToken(), checkoutToken)) {
            throw new ResourceNotFoundException("Order not found");
        }

        if (order.getStatus() == PaymentOrderStatus.COMPLETED) {
            throw new InvalidStateException("Order has already been completed");
        }
        if (order.getStatus() != PaymentOrderStatus.CREATED
                && order.getStatus() != PaymentOrderStatus.PENDING
                && order.getStatus() != PaymentOrderStatus.PROCESSING) {
            throw new InvalidStateException("Order is no longer payable: " + order.getStatus());
        }
        if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(Instant.now())) {
            order.setStatus(PaymentOrderStatus.EXPIRED);
            orderRepository.save(order);
            throw new InvalidStateException("Checkout session has expired");
        }

        PaymentGatewayAdapter adapter = getAdapter(order.getProvider());
        User user = userRepository.findByUserId(order.getUserId()).orElse(null);
        PaymentGatewayAdapter.CheckoutSessionResult result =
                adapter.createCheckoutSession(order, buildCheckoutContext(order, user));

        if (result.requiresFormPost()) {
            return new HostedCheckout(order, result.checkoutUrl(), result.formFields(), true);
        }
        return new HostedCheckout(order, result.checkoutUrl(), Map.of(), false);
    }

    // ---------------------------------------------------------------------
    // Provider callbacks
    // ---------------------------------------------------------------------

    /**
     * Backwards-compatible overload used by the mock adapter and unit tests.
     */
    public boolean processWebhook(String providerName, String rawBody, String signatureHeader) {
        Map<String, String> headers = signatureHeader == null
                ? Map.of()
                : Map.of("X-Signature", signatureHeader);
        return processWebhook(providerName, rawBody, headers, Map.of());
    }

    /**
     * Verifies and applies a provider callback. Returns {@code true} for both a
     * fresh fulfillment and a correctly identified duplicate (so providers stop
     * retrying), and throws for forged/invalid callbacks so they can be alerted on.
     */
    public boolean processWebhook(String providerName, String rawBody,
                                  Map<String, String> headers, Map<String, String> formParams) {
        PaymentGatewayAdapter adapter = getAdapter(providerName);
        PaymentNotification notification = adapter.verifyAndParseNotification(
                new PaymentGatewayAdapter.WebhookRequest(rawBody, headers, formParams));

        if (notification == null || notification.orderId() == null || notification.orderId().isBlank()) {
            throw new InvalidStateException("Callback did not contain an order reference");
        }

        String provider = adapter.getProviderName();
        if (eventRepository.existsByProviderAndProviderEventId(provider, notification.providerEventId())) {
            log.info("Duplicate {} callback {} acknowledged without side effects",
                    provider, notification.providerEventId());
            return true;
        }

        return switch (notification.outcome()) {
            case PAID -> handlePaid(provider, notification);
            case PENDING -> handleNonPaid(provider, notification, PaymentOrderStatus.PROCESSING, "PENDING");
            case CANCELLED -> handleNonPaid(provider, notification, PaymentOrderStatus.CANCELLED, "CANCELLED");
            case FAILED -> handleNonPaid(provider, notification, PaymentOrderStatus.FAILED, "FAILED");
            case CHARGEDBACK, REFUNDED -> handleRefundSignal(provider, notification);
        };
    }

    private boolean handlePaid(String provider, PaymentNotification notification) {
        try {
            Boolean result = transactionTemplate.execute(status -> {
                PaymentOrder order = orderRepository.findByOrderId(notification.orderId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Order not found for webhook: " + notification.orderId()));

                if (!provider.equalsIgnoreCase(order.getProvider())) {
                    throw new InvalidStateException("Callback provider does not match the order provider");
                }

                if (order.getStatus() == PaymentOrderStatus.COMPLETED) {
                    recordDuplicateEvent(provider, notification, order.getOrderId());
                    return true;
                }
                if (order.getStatus() == PaymentOrderStatus.REFUNDED
                        || order.getStatus() == PaymentOrderStatus.REFUND_PENDING) {
                    throw new InvalidStateException("Order is in a refund lifecycle and cannot be credited");
                }
                if (order.getStatus() == PaymentOrderStatus.CANCELLED
                        || order.getStatus() == PaymentOrderStatus.EXPIRED) {
                    throw new InvalidStateException(
                            "Order " + order.getOrderId() + " was already closed as " + order.getStatus());
                }

                validateAmountAndCurrency(order, notification);

                // The order snapshot is the only source of truth for the credit.
                order.setStatus(PaymentOrderStatus.COMPLETED);
                order.setCompletedAt(Instant.now());
                order.setProviderStatusCode(notification.statusCode());
                if (notification.providerPaymentId() != null) {
                    order.setProviderPaymentId(notification.providerPaymentId());
                }
                orderRepository.save(order);

                String description = "Real Coin Top-up: " + order.getCoinsToCredit()
                        + " coins (" + order.getBundleSku() + ", " + provider + ")";
                walletService.creditRealCoins(
                        order.getUserId(),
                        order.getCoinsToCredit(),
                        "PAYMENT_ORDER",
                        order.getOrderId(),
                        description
                );

                eventRepository.save(new PaymentEvent(
                        provider,
                        notification.providerEventId(),
                        order.getOrderId(),
                        "PROCESSED",
                        notification.sanitizedPayload()
                ));

                // Outbox row shares the transaction with the credit: the player is
                // notified only for a credit that actually committed.
                outboxService.enqueue(
                        OutboxService.WALLET_CREDITED,
                        "payment_order",
                        order.getOrderId(),
                        Map.of(
                                "userId", order.getUserId(),
                                "coins", order.getCoinsToCredit(),
                                "orderId", order.getOrderId(),
                                "provider", provider
                        )
                );

                log.info("Fulfilled {} top-up order {}: +{} RC to user {} (paymentId={})",
                        provider, order.getOrderId(), order.getCoinsToCredit(),
                        order.getUserId(), notification.providerPaymentId());
                return true;
            });
            return Boolean.TRUE.equals(result);
        } catch (DuplicateKeyException | DuplicateResourceException e) {
            // A concurrent retry won the race. The unique ledger reference and
            // payment_events index guarantee exactly one credit.
            log.info("Concurrent duplicate callback for order {} ignored: {}",
                    notification.orderId(), e.getClass().getSimpleName());
            return true;
        }
    }

    private boolean handleNonPaid(String provider, PaymentNotification notification,
                                  PaymentOrderStatus target, String eventStatus) {
        try {
            Boolean result = transactionTemplate.execute(status -> {
                PaymentOrder order = orderRepository.findByOrderId(notification.orderId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Order not found for webhook: " + notification.orderId()));

                // Never regress a terminal order because a delayed/out-of-order
                // "failed"/"pending" callback arrived after it was closed.
                if (order.getStatus() == PaymentOrderStatus.COMPLETED
                        || order.getStatus() == PaymentOrderStatus.REFUNDED
                        || order.getStatus() == PaymentOrderStatus.REFUND_PENDING
                        || order.getStatus() == PaymentOrderStatus.CANCELLED
                        || order.getStatus() == PaymentOrderStatus.EXPIRED) {
                    log.info("Ignoring {} callback for order {} in terminal status {}",
                            eventStatus, order.getOrderId(), order.getStatus());
                    recordDuplicateEvent(provider, notification, order.getOrderId());
                    return true;
                }

                if (order.getStatus() != target) {
                    order.setStatus(target);
                    if (notification.providerPaymentId() != null) {
                        order.setProviderPaymentId(notification.providerPaymentId());
                    }
                    order.setProviderStatusCode(notification.statusCode());
                    orderRepository.save(order);
                }

                eventRepository.save(new PaymentEvent(
                        provider,
                        notification.providerEventId(),
                        order.getOrderId(),
                        eventStatus,
                        notification.sanitizedPayload()
                ));
                log.info("Order {} transitioned to {} via {} callback",
                        order.getOrderId(), target, provider);
                return true;
            });
            return Boolean.TRUE.equals(result);
        } catch (DuplicateKeyException e) {
            return true;
        }
    }

    private boolean handleRefundSignal(String provider, PaymentNotification notification) {
        try {
            Boolean result = transactionTemplate.execute(status -> {
                PaymentOrder order = orderRepository.findByOrderId(notification.orderId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Order not found for webhook: " + notification.orderId()));

                // Never reverse the wallet automatically here: the player may
                // already have spent the coins. Support must review the case.
                if (order.getStatus() == PaymentOrderStatus.COMPLETED) {
                    order.setStatus(PaymentOrderStatus.REFUND_PENDING);
                    order.setProviderStatusCode(notification.statusCode());
                    if (notification.providerPaymentId() != null) {
                        order.setProviderPaymentId(notification.providerPaymentId());
                    }
                    orderRepository.save(order);
                }

                eventRepository.save(new PaymentEvent(
                        provider,
                        notification.providerEventId(),
                        order.getOrderId(),
                        "REFUND_SIGNAL",
                        notification.sanitizedPayload()
                ));
                log.warn("Refund/chargeback signal for order {} ({}). Marked REFUND_PENDING for review.",
                        order.getOrderId(), notification.statusCode());
                return true;
            });
            return Boolean.TRUE.equals(result);
        } catch (DuplicateKeyException e) {
            return true;
        }
    }

    private void recordDuplicateEvent(String provider, PaymentNotification notification, String orderId) {
        if (eventRepository.existsByProviderAndProviderEventId(provider, notification.providerEventId())) {
            return;
        }
        eventRepository.save(new PaymentEvent(
                provider,
                notification.providerEventId(),
                orderId,
                "DUPLICATE_IGNORED",
                notification.sanitizedPayload()
        ));
    }

    private void validateAmountAndCurrency(PaymentOrder order, PaymentNotification notification) {
        if (notification.amountMinor() == null) {
            throw new InvalidStateException("Paid callback did not include an amount");
        }
        if (notification.amountMinor() != order.getAmountMinor()) {
            log.error("Amount mismatch for order {}: expected {} minor units, provider sent {}",
                    order.getOrderId(), order.getAmountMinor(), notification.amountMinor());
            throw new InvalidStateException("Callback amount does not match the order snapshot");
        }
        if (notification.currency() == null
                || !notification.currency().equalsIgnoreCase(order.getCurrency())) {
            log.error("Currency mismatch for order {}: expected {}, provider sent {}",
                    order.getOrderId(), order.getCurrency(), notification.currency());
            throw new InvalidStateException("Callback currency does not match the order snapshot");
        }
    }

    // ---------------------------------------------------------------------
    // Reconciliation / manual completion
    // ---------------------------------------------------------------------

    /**
     * Finance/admin reconciliation. Uses the authenticated PayHere Retrieval API
     * (not a client-supplied status) to recover paid-but-unfulfilled orders.
     */
    public ReconciliationResult reconcileOrder(String orderId) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!"payhere".equalsIgnoreCase(order.getProvider())) {
            throw new InvalidStateException("Reconciliation is only supported for payhere orders");
        }

        PaymentOrderStatus before = order.getStatus();
        Optional<PayHereMerchantApiClient.PayHerePaymentRecord> record =
                payHereMerchantApiClient.retrievePayment(orderId);

        if (record.isEmpty()) {
            return new ReconciliationResult(order, "NO_PAYMENT_FOUND", before);
        }

        PayHereMerchantApiClient.PayHerePaymentRecord payment = record.get();
        String status = payment.status() == null ? "" : payment.status().toUpperCase(Locale.ROOT);

        switch (status) {
            case "RECEIVED" -> {
                PaymentNotification notification = new PaymentNotification(
                        "reconcile:" + (payment.paymentId() == null ? orderId : payment.paymentId()),
                        orderId,
                        payment.paymentId(),
                        PaymentNotification.Outcome.PAID,
                        toMinor(payment.amount()),
                        payment.currency(),
                        "2",
                        "{\"source\":\"payhere-retrieval\",\"status\":\"RECEIVED\"}"
                );
                handlePaid("payhere", notification);
                PaymentOrder refreshed = orderRepository.findByOrderId(orderId).orElse(order);
                return new ReconciliationResult(refreshed, "RECEIVED", before);
            }
            case "REFUNDED", "CHARGEBACKED", "REFUND REQUESTED", "REFUND PROCESSING" -> {
                // "REFUND REQUESTED"/"REFUND PROCESSING" mean the money is still
                // moving, so they share the non-terminal REFUND_PENDING review
                // path instead of being treated as a completed reversal.
                boolean completedReversal = "REFUNDED".equals(status) || "CHARGEBACKED".equals(status);
                PaymentNotification notification = new PaymentNotification(
                        "reconcile:" + (payment.paymentId() == null ? orderId : payment.paymentId()) + ":" + status,
                        orderId,
                        payment.paymentId(),
                        completedReversal
                                ? ("REFUNDED".equals(status)
                                    ? PaymentNotification.Outcome.REFUNDED
                                    : PaymentNotification.Outcome.CHARGEDBACK)
                                : PaymentNotification.Outcome.REFUNDED,
                        toMinor(payment.amount()),
                        payment.currency(),
                        status,
                        "{\"source\":\"payhere-retrieval\",\"status\":\"" + status + "\"}"
                );
                handleRefundSignal("payhere", notification);
                PaymentOrder refreshed = orderRepository.findByOrderId(orderId).orElse(order);
                return new ReconciliationResult(refreshed, status, before);
            }
            default -> {
                return new ReconciliationResult(order, status.isBlank() ? "UNKNOWN" : status, before);
            }
        }
    }

    /**
     * Development-only path used by the mock hosted page. Disabled unless the
     * mock provider feature flag is on.
     */
    public boolean mockCompleteOrder(String orderId) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!"mock".equalsIgnoreCase(order.getProvider())) {
            throw new InvalidStateException("Order is not a mock-provider order");
        }
        // Fail closed if the mock flag was turned off after the order was created.
        getAdapter("mock");

        String eventId = "evt_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String payloadJson = String.format(
                "{\"type\":\"payment.succeeded\",\"eventId\":\"%s\",\"orderId\":\"%s\",\"amountMinor\":%d,\"currency\":\"%s\",\"paymentId\":\"mockpay_%s\"}",
                eventId, order.getOrderId(), order.getAmountMinor(), order.getCurrency(), eventId
        );

        try {
            com.railbound.portal.payments.MockPaymentAdapter mock =
                    (com.railbound.portal.payments.MockPaymentAdapter) getAdapter("mock");
            String signature = mock.computeHmacSha256(payloadJson, mock.getSecret());
            return processWebhook("mock", payloadJson, signature);
        } catch (InvalidSignatureException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Mock webhook signing failed", e);
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private PaymentGatewayAdapter.CheckoutContext buildCheckoutContext(PaymentOrder order, User user) {
        String displayName = user != null && user.getDisplayName() != null
                ? user.getDisplayName().trim()
                : "Railbound Player";
        String[] parts = displayName.split("\\s+", 2);
        String firstName = parts.length > 0 && !parts[0].isBlank() ? parts[0] : "Railbound";
        String lastName = parts.length > 1 && !parts[1].isBlank() ? parts[1] : "Player";

        String email = user != null && user.getEmail() != null && !user.getEmail().isBlank()
                ? user.getEmail().trim()
                : "player@railbound.invalid";

        return new PaymentGatewayAdapter.CheckoutContext(
                frontendBaseUrl + "/checkout/return?orderId=" + order.getOrderId(),
                frontendBaseUrl + "/checkout/return?orderId=" + order.getOrderId() + "&cancelled=1",
                publicBaseUrl + "/api/v1/payments/webhooks/" + order.getProvider(),
                firstName,
                lastName,
                email,
                // The portal does not yet collect a phone number. PayHere only
                // needs a syntactically valid value to pre-fill the hosted page.
                "0770000000",
                "No.1 Railbound Sector",
                "Colombo",
                "Sri Lanka"
        );
    }

    /**
     * The bridge page must be served from the <em>frontend</em> origin because
     * PayHere validates the browser's Referer against the merchant's registered
     * domain. Serving the auto-submit form from the API origin would cause
     * "Unauthorized Payment Request" errors in production.
     */
    private String hostedBridgeUrl(String orderId, String checkoutToken) {
        return frontendBaseUrl + "/checkout/hosted?orderId=" + orderId + "&token=" + checkoutToken;
    }

    private String generateCheckoutToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static long toMinor(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidStateException("Provider payment record did not include an amount");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record HostedCheckout(
            PaymentOrder order,
            String actionUrl,
            Map<String, String> formFields,
            boolean requiresFormPost
    ) {}

    public record ReconciliationResult(
            PaymentOrder order,
            String providerStatus,
            PaymentOrderStatus previousStatus
    ) {}
}
