package com.railbound.portal.payments;

import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "PayHere hosted top-up checkout, callbacks and order status")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/checkout-sessions")
    @Operation(summary = "Create a hosted PayHere checkout session for a coin bundle")
    public ResponseEntity<Map<String, Object>> createCheckoutSession(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, String> request) {
        String userId = SecurityUtils.getCurrentUserId();
        String bundleSku = request.get("bundleSku");
        String provider = request.get("provider");

        PaymentOrder order = paymentService.createCheckoutSession(userId, bundleSku, provider, idempotencyKey);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", order.getOrderId());
        response.put("status", order.getStatus().name());
        response.put("checkoutUrl", paymentService.checkoutUrlFor(order));
        response.put("provider", order.getProvider());
        response.put("bundleSku", order.getBundleSku());
        response.put("coinsToCredit", order.getCoinsToCredit());
        response.put("amountMinor", order.getAmountMinor());
        response.put("currency", order.getCurrency());
        response.put("expiresAt", order.getExpiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
     * PayHere posts {@code application/x-www-form-urlencoded}, not JSON. We read
     * the servlet request directly so the same endpoint can also accept the
     * development mock's JSON payload.
     *
     * <p>Signature verification happens inside the provider adapter before any
     * state change; an unsigned/failed callback results in HTTP 401.</p>
     */
    @PostMapping("/webhooks/{provider}")
    @Operation(summary = "Inbound payment provider callback (PayHere form POST)")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @PathVariable String provider,
            @RequestHeader Map<String, String> headers,
            HttpServletRequest request) throws IOException {

        String contentType = request.getContentType() == null
                ? ""
                : request.getContentType().toLowerCase();

        Map<String, String> formParams = new LinkedHashMap<>();
        String rawBody;

        if (contentType.contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
            // PayHere sends form params. The container parses them; we then
            // rebuild a deterministic body for audit logging only.
            request.getParameterMap().forEach((key, values) ->
                    formParams.put(key, values.length > 0 ? values[0] : ""));
            rawBody = toFormEncoded(formParams);
        } else {
            rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }

        boolean success = paymentService.processWebhook(provider, rawBody, headers, formParams);
        return ResponseEntity.ok(Map.of("status", success ? "PROCESSED" : "IGNORED"));
    }

    @PostMapping("/mock-complete/{orderId}")
    @Operation(summary = "Development-only simulated checkout completion (mock provider must be enabled)")
    public ResponseEntity<Map<String, Object>> mockComplete(@PathVariable String orderId) {
        boolean result = paymentService.mockCompleteOrder(orderId);
        return ResponseEntity.ok(Map.of("success", result, "orderId", orderId));
    }

    private static String toFormEncoded(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }
}
