package com.railbound.portal.payments;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Token-gated bridge used by the frontend/Unity hosted-checkout page.
 *
 * <p>The actual form POST must originate from the frontend origin so the
 * browser's {@code Referer} matches the domain registered in the PayHere
 * merchant portal. This endpoint therefore returns the action URL + signed form
 * fields as JSON; the Next.js page builds and submits the form.</p>
 *
 * <p>No bearer token is required because the client opens this URL in an
 * external browser, but the opaque per-order token prevents enumeration.</p>
 */
@RestController
@RequestMapping("/api/v1/payments/hosted")
@Tag(name = "Payments", description = "Hosted checkout bridge")
public class HostedCheckoutController {

    private final PaymentService paymentService;

    public HostedCheckoutController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Fetch signed form fields for the provider hosted checkout")
    public ResponseEntity<Map<String, Object>> getHostedCheckout(
            @PathVariable String orderId,
            @RequestParam("token") String token) {

        PaymentService.HostedCheckout hosted = paymentService.prepareHostedCheckout(orderId, token);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", hosted.order().getOrderId());
        response.put("provider", hosted.order().getProvider());
        response.put("method", hosted.requiresFormPost() ? "POST" : "GET");
        response.put("actionUrl", hosted.actionUrl());
        response.put("fields", hosted.formFields());
        response.put("bundleSku", hosted.order().getBundleSku());
        response.put("coinsToCredit", hosted.order().getCoinsToCredit());
        response.put("amountMinor", hosted.order().getAmountMinor());
        response.put("currency", hosted.order().getCurrency());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(response);
    }
}
