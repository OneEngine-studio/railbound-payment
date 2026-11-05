package com.railbound.portal.payments;

import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
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

    /** Request body for opening the PayHere hosted page. */
    public record HostedFormRequest(String checkoutToken, BillingDetails billing) {}

    @GetMapping("/providers")
    @Operation(summary = "List payment providers that are currently enabled")
    public ResponseEntity<List<Map<String, Object>>> providers() {
        return ResponseEntity.ok(paymentService.listProviders());
    }

    @PostMapping("/checkout-sessions")
    @Operation(summary = "Create hosted checkout session for a coin bundle")
    public ResponseEntity<Map<String, Object>> createCheckoutSession(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, String> request) {
        String userId = SecurityUtils.getCurrentUserId();
        String bundleSku = request.get("bundleSku");
        String provider = request.get("provider");

        CheckoutStart start = paymentService.startCheckout(userId, bundleSku, provider, idempotencyKey);
        PaymentOrder order = start.order();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", order.getOrderId());
        body.put("status", order.getStatus().name());
        body.put("provider", order.getProvider());
        body.put("checkoutUrl", start.checkoutUrl());
        body.put("bundleSku", order.getBundleSku());
        body.put("coinsToCredit", order.getCoinsToCredit());
        body.put("amountMinor", order.getAmountMinor());
        body.put("currency", order.getCurrency());
        body.put("expiresAt", order.getExpiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/orders/{orderId}/hosted-form")
    @Operation(summary = "Get the signed PayHere form fields for an order (authorized by the one-time checkout token)")
    public ResponseEntity<HostedForm> hostedForm(@PathVariable String orderId, @RequestBody HostedFormRequest request) {
        return ResponseEntity.ok(paymentService.buildHostedForm(orderId, request.checkoutToken(), request.billing()));
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

    /**
     * Provider server-to-server callback. Public by necessity (the provider has no login); trust
     * comes entirely from the provider's signature, verified over the raw body inside the adapter.
     * PayHere posts application/x-www-form-urlencoded; the mock provider posts JSON with an
     * X-Signature header. The body is read as a raw string so the signature is checked over exactly
     * what was sent.
     */
    @PostMapping("/webhooks/{provider}")
    @Operation(summary = "Inbound payment provider notification (signature verified)")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @PathVariable String provider,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        boolean success = paymentService.processWebhook(provider, rawBody, signature);
        return ResponseEntity.ok(Map.of("status", success ? "PROCESSED" : "IGNORED"));
    }

    @PostMapping("/mock-complete/{orderId}")
    @Operation(summary = "DEV ONLY: simulate the mock hosted checkout completing (owner only, disabled in production)")
    public ResponseEntity<Map<String, Object>> mockComplete(@PathVariable String orderId) {
        boolean result = paymentService.mockCompleteOrder(orderId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(Map.of("success", result, "orderId", orderId));
    }
}
