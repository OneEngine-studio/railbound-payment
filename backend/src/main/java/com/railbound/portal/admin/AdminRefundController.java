package com.railbound.portal.admin;

import com.railbound.portal.refunds.RefundCase;
import com.railbound.portal.refunds.RefundService;
import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/refunds")
@PreAuthorize("hasAnyRole('ADMIN','FINANCE_SUPPORT','SUPER_ADMIN')")
@Tag(name = "Admin Refunds", description = "Refund and chargeback cases with non-negative coin reversal")
public class AdminRefundController {

    private final RefundService refundService;

    public AdminRefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @GetMapping
    @Operation(summary = "List refund and chargeback cases")
    public ResponseEntity<List<RefundCase>> list() {
        return ResponseEntity.ok(refundService.listCases());
    }

    @PostMapping
    @Operation(summary = "Open a refund for a COMPLETED order and reverse the coins if the player still holds them")
    public ResponseEntity<RefundCase> create(@RequestBody Map<String, String> body) {
        RefundCase created = refundService.createRefund(
                body.get("orderId"), body.get("reason"), SecurityUtils.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{refundId}/retry")
    @Operation(summary = "Retry the coin reversal for a NEEDS_REVIEW case")
    public ResponseEntity<RefundCase> retry(@PathVariable String refundId) {
        return ResponseEntity.ok(refundService.retryReversal(refundId, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/{refundId}/confirm")
    @Operation(summary = "Confirm the money was refunded to the customer through PayHere")
    public ResponseEntity<RefundCase> confirm(@PathVariable String refundId, @RequestBody(required = false) Map<String, String> body) {
        String providerRefundId = body == null ? null : body.get("providerRefundId");
        return ResponseEntity.ok(refundService.markProviderRefunded(refundId, providerRefundId, SecurityUtils.getCurrentUserId()));
    }
}
