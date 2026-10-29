package com.railbound.portal.payments;

import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Hosted top-up checkout and webhook fulfillment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/checkout-sessions")
    @Operation(summary = "Create hosted checkout session for a coin bundle")
    public ResponseEntity<Map<String, Object>> createCheckoutSession(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, String> request) {
        String userId = SecurityUtils.getCurrentUserId();
        String bundleSku = request.get("bundleSku");
        String provider = request.getOrDefault("provider", "mock");

        PaymentOrder order = paymentService.createCheckoutSession(userId, bundleSku, provider, idempotencyKey);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "orderId", order.getOrderId(),
                "status", order.getStatus().name(),
                "checkoutUrl", "http://localhost:3000/checkout/hosted-mock?orderId=" + order.getOrderId(),
                "bundleSku", order.getBundleSku(),
                "coinsToCredit", order.getCoinsToCredit(),
                "amountMinor", order.getAmountMinor(),
                "currency", order.getCurrency(),
                "expiresAt", order.getExpiresAt()
        ));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Poll verified payment order status")
    public ResponseEntity<PaymentOrder> getOrder(@PathVariable String orderId) {
        String userId = SecurityUtils.getCurrentUserId();
        PaymentOrder order = paymentService.getOrderForUser(orderId, userId);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/orders")
    @Operation(summary = "Get player top-up order history")
    public ResponseEntity<List<PaymentOrder>> getMyOrders() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(paymentService.getOrdersForUser(userId));
    }

    @PostMapping("/webhooks/{provider}")
    @Operation(summary = "Inbound payment provider webhook callback with cryptographic signature")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @PathVariable String provider,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        boolean success = paymentService.processWebhook(provider, rawBody, signature);
        return ResponseEntity.ok(Map.of("status", success ? "PROCESSED" : "IGNORED"));
    }

    @PostMapping("/mock-complete/{orderId}")
    @Operation(summary = "Convenience test trigger to simulate a hosted checkout payment completion")
    public ResponseEntity<Map<String, Object>> mockComplete(@PathVariable String orderId) {
        boolean result = paymentService.mockCompleteOrder(orderId);
        return ResponseEntity.ok(Map.of("success", result, "orderId", orderId));
    }
}
