package com.railbound.portal.admin;

import com.railbound.portal.payments.PaymentOrder;
import com.railbound.portal.payments.PaymentService;
import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasAnyRole('ADMIN','FINANCE_SUPPORT','SUPER_ADMIN')")
@Tag(name = "Admin Payments", description = "Order reconciliation and review of mismatched payments")
public class AdminPaymentController {

    private final PaymentService paymentService;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/review")
    @Operation(summary = "Orders parked because a payment did not match the order (amount/currency)")
    public ResponseEntity<List<PaymentOrder>> awaitingReview() {
        return ResponseEntity.ok(paymentService.getOrdersAwaitingReview());
    }

    @PostMapping("/{orderId}/reconcile")
    @Operation(summary = "Check one order's status against the wallet ledger")
    public ResponseEntity<Map<String, Object>> reconcile(@PathVariable String orderId) {
        return ResponseEntity.ok(paymentService.reconcileOrder(orderId, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/{orderId}/review")
    @Operation(summary = "Approve (credit coins) or reject a REVIEW_REQUIRED order after checking the PayHere dashboard")
    public ResponseEntity<PaymentOrder> resolveReview(@PathVariable String orderId, @RequestBody Map<String, Object> body) {
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        return ResponseEntity.ok(paymentService.resolveReview(orderId, approve, note, SecurityUtils.getCurrentUserId()));
    }
}
