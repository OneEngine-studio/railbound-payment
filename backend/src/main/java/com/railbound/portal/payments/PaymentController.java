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
                "checkoutUrl", order.getCheckoutUrl() == null ? "" : order.getCheckoutUrl(),
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
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature,
            @RequestBody String rawBody) {
        boolean success = paymentService.processWebhook(provider, rawBody, stripeSignature != null ? stripeSignature : signature);
        return ResponseEntity.ok(Map.of("status", success ? "PROCESSED" : "IGNORED"));
    }

    /** Where Stripe sends the player's browser afterwards: a plain page. Credit is decided by the webhook alone. */
    @GetMapping(value = "/return", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> checkoutReturn(@RequestParam(value = "result", defaultValue = "success") String result) {
        boolean ok = "success".equals(result);
        String title = ok ? "Payment received" : "Checkout cancelled";
        String text = ok ? "Your coins arrive in the OneEngine launcher in a moment. You can close this tab."
                : "Nothing was charged. You can close this tab and try again in the launcher.";
        String html = "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<title>" + title + "</title><body style=\"margin:0;min-height:100vh;display:grid;place-items:center;"
                + "background:#24100F;color:#fff;font-family:system-ui,sans-serif;text-align:center\"><main><h1>" + title
                + "</h1><p style=\"color:#d1b4b5\">" + text + "</p></main></body>";
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
                .body(html);
    }

    @PostMapping("/mock-complete/{orderId}")
    @Operation(summary = "Convenience test trigger to simulate a hosted checkout payment completion")
    public ResponseEntity<Map<String, Object>> mockComplete(@PathVariable String orderId) {
        boolean result = paymentService.mockCompleteOrder(orderId);
        return ResponseEntity.ok(Map.of("success", result, "orderId", orderId));
    }
}
