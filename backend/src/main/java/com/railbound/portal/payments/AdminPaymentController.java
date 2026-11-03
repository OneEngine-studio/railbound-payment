package com.railbound.portal.payments;

import com.railbound.portal.refunds.RefundCase;
import com.railbound.portal.refunds.RefundService;
import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finance-only payment operations. Reconciliation never trusts a browser-supplied
 * status: it asks PayHere's merchant API and then runs the same verified
 * fulfillment path as a webhook.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Payments", description = "Reconciliation and controlled refund workflows")
public class AdminPaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    public AdminPaymentController(PaymentService paymentService, RefundService refundService) {
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    @PostMapping("/payments/{orderId}/reconcile")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','FINANCE_SUPPORT')")
    @Operation(summary = "Reconcile an order against the PayHere Retrieval API")
    public ResponseEntity<Map<String, Object>> reconcile(@PathVariable String orderId) {
        PaymentService.ReconciliationResult result = paymentService.reconcileOrder(orderId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", result.order().getOrderId());
        response.put("previousStatus", result.previousStatus());
        response.put("currentStatus", result.order().getStatus());
        response.put("providerStatus", result.providerStatus());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/refunds")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','FINANCE_SUPPORT')")
    @Operation(summary = "List refund cases")
    public ResponseEntity<List<RefundCase>> listRefunds() {
        return ResponseEntity.ok(refundService.getAllRefundCases());
    }

    @PostMapping("/refunds")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','FINANCE_SUPPORT')")
    @Operation(summary = "Request an audited PayHere refund and Real Coin reversal")
    public ResponseEntity<RefundCase> createRefund(@RequestBody Map<String, String> request) {
        String orderId = request.get("orderId");
        String reason = request.get("reason");
        String actor = SecurityUtils.getCurrentUserId();
        RefundCase refund = refundService.requestRefund(orderId, reason, actor);
        return ResponseEntity.ok(refund);
    }
}
