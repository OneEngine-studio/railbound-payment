package com.railbound.portal.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railbound.portal.bundles.CoinBundle;
import com.railbound.portal.bundles.CoinBundleService;
import com.railbound.portal.common.InvalidSignatureException;
import com.railbound.portal.common.InvalidStateException;
import com.railbound.portal.common.ResourceNotFoundException;
import com.railbound.portal.wallet.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentOrderRepository orderRepository;
    private final PaymentEventRepository eventRepository;
    private final CoinBundleService bundleService;
    private final WalletService walletService;
    private final MockPaymentAdapter mockPaymentAdapter;
    private final ObjectMapper objectMapper;

    public PaymentService(
            PaymentOrderRepository orderRepository,
            PaymentEventRepository eventRepository,
            CoinBundleService bundleService,
            WalletService walletService,
            MockPaymentAdapter mockPaymentAdapter,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.bundleService = bundleService;
        this.walletService = walletService;
        this.mockPaymentAdapter = mockPaymentAdapter;
        this.objectMapper = objectMapper;
    }

    public PaymentGatewayAdapter getAdapter(String providerName) {
        if ("mock".equalsIgnoreCase(providerName)) {
            return mockPaymentAdapter;
        }
        throw new ResourceNotFoundException("Unsupported payment provider: " + providerName);
    }

    public PaymentOrder createCheckoutSession(String userId, String bundleSku, String provider, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<PaymentOrder> existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent checkout request detected for user {} and key {}", userId, idempotencyKey);
                return existing.get();
            }
        }

        CoinBundle bundle = bundleService.getBundleBySku(bundleSku);
        String selectedProvider = (provider != null && !provider.isBlank()) ? provider : "mock";
        PaymentGatewayAdapter adapter = getAdapter(selectedProvider);

        String orderId = "ord_" + UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);

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

        PaymentGatewayAdapter.CheckoutSessionResult sessionResult = adapter.createCheckoutSession(
                order,
                "http://localhost:3000/checkout/return?orderId=" + orderId
        );
        order.setProviderSessionId(sessionResult.providerSessionId());
        return orderRepository.save(order);
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

    /**
     * Authoritative webhook handler:
     * 1. Validates cryptographic signature
     * 2. Deduplicates event
     * 3. Executes single-credit MongoDB transaction
     */
    @Transactional
    public boolean processWebhook(String providerName, String rawBody, String signatureHeader) {
        PaymentGatewayAdapter adapter = getAdapter(providerName);

        if (!adapter.verifyWebhookSignature(rawBody, signatureHeader)) {
            log.error("Payment webhook signature verification failed for provider {}", providerName);
            throw new InvalidSignatureException("Webhook signature verification failed");
        }

        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            String eventId = payload.has("eventId") ? payload.get("eventId").asText() : "evt_" + UUID.randomUUID();
            String orderId = payload.has("orderId") ? payload.get("orderId").asText() : null;
            String eventType = payload.has("type") ? payload.get("type").asText() : "payment.succeeded";

            if (orderId == null || orderId.isBlank()) {
                throw new InvalidStateException("Missing orderId in webhook payload");
            }

            if (eventRepository.existsByProviderAndProviderEventId(providerName, eventId)) {
                log.info("Duplicate webhook event {} acknowledged without re-crediting", eventId);
                return true;
            }

            PaymentOrder order = orderRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found for webhook: " + orderId));

            if (order.getStatus() == PaymentOrderStatus.COMPLETED) {
                log.info("Order {} already completed; recording duplicate callback event", orderId);
                eventRepository.save(new PaymentEvent(providerName, eventId, orderId, "DUPLICATE_IGNORED", rawBody));
                return true;
            }

            // Mark order COMPLETED
            order.setStatus(PaymentOrderStatus.COMPLETED);
            order.setCompletedAt(Instant.now());
            orderRepository.save(order);

            // Authoritatively credit wallet with snapshot coins and append immutable ledger entry
            String desc = "Real Coin Top-up: " + order.getCoinsToCredit() + " coins (" + order.getBundleSku() + ")";
            walletService.creditRealCoins(
                    order.getUserId(),
                    order.getCoinsToCredit(),
                    "PAYMENT_ORDER",
                    order.getOrderId(),
                    desc
            );

            // Save verified payment event
            eventRepository.save(new PaymentEvent(providerName, eventId, orderId, "PROCESSED", rawBody));
            log.info("Successfully fulfilled top-up order {} with {} coins for user {}",
                    orderId, order.getCoinsToCredit(), order.getUserId());
            return true;

        } catch (InvalidSignatureException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error processing webhook payload", e);
            throw new InvalidStateException("Failed to process payment webhook: " + e.getMessage());
        }
    }

    /**
     * Convenience simulation for mock provider checkout page.
     * Generates a signed webhook and invokes the authoritative webhook verification path.
     */
    public boolean mockCompleteOrder(String orderId) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        String eventId = "evt_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String payloadJson = String.format(
                "{\"type\":\"payment.succeeded\",\"eventId\":\"%s\",\"orderId\":\"%s\",\"amountMinor\":%d,\"currency\":\"%s\"}",
                eventId, order.getOrderId(), order.getAmountMinor(), order.getCurrency()
        );

        try {
            String signature = mockPaymentAdapter.computeHmacSha256(payloadJson, mockPaymentAdapter.getSecret());
            return processWebhook("mock", payloadJson, signature);
        } catch (Exception e) {
            throw new RuntimeException("Mock webhook signing failed", e);
        }
    }
}
