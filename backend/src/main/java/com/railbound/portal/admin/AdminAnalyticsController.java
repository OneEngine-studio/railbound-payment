package com.railbound.portal.admin;

import com.railbound.portal.audit.AuditLog;
import com.railbound.portal.audit.AuditService;
import com.railbound.portal.identity.User;
import com.railbound.portal.identity.UserRepository;
import com.railbound.portal.outbox.OutboxService;
import com.railbound.portal.payments.PaymentOrder;
import com.railbound.portal.payments.PaymentOrderRepository;
import com.railbound.portal.payments.PaymentOrderStatus;
import com.railbound.portal.refunds.RefundCaseRepository;
import com.railbound.portal.shop.ShopItemRepository;
import com.railbound.portal.wallet.Wallet;
import com.railbound.portal.wallet.WalletRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Oversight", description = "Analytics, orders, players and audit logging")
public class AdminAnalyticsController {

    private final UserRepository userRepository;
    private final PaymentOrderRepository orderRepository;
    private final WalletRepository walletRepository;
    private final ShopItemRepository itemRepository;
    private final RefundCaseRepository refundCaseRepository;
    private final OutboxService outboxService;
    private final AuditService auditService;

    public AdminAnalyticsController(
            UserRepository userRepository,
            PaymentOrderRepository orderRepository,
            WalletRepository walletRepository,
            ShopItemRepository itemRepository,
            RefundCaseRepository refundCaseRepository,
            OutboxService outboxService,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.walletRepository = walletRepository;
        this.itemRepository = itemRepository;
        this.refundCaseRepository = refundCaseRepository;
        this.outboxService = outboxService;
        this.auditService = auditService;
    }

    @GetMapping("/analytics/summary")
    @Operation(summary = "Admin dashboard KPI summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        long totalPlayers = userRepository.count();
        List<PaymentOrder> allOrders = orderRepository.findAll();
        long completedOrders = allOrders.stream().filter(o -> o.getStatus() == PaymentOrderStatus.COMPLETED).count();
        long totalRevenueMinor = allOrders.stream()
                .filter(o -> o.getStatus() == PaymentOrderStatus.COMPLETED)
                .mapToLong(PaymentOrder::getAmountMinor)
                .sum();
        long refundPending = allOrders.stream()
                .filter(o -> o.getStatus() == PaymentOrderStatus.REFUND_PENDING)
                .count();

        long totalCoinsCirculation = walletRepository.findAll().stream()
                .mapToLong(Wallet::getRealCoins)
                .sum();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalPlayers", totalPlayers);
        summary.put("totalOrders", allOrders.size());
        summary.put("completedOrders", completedOrders);
        summary.put("totalRevenueMinor", totalRevenueMinor);
        summary.put("totalRevenueUsd", totalRevenueMinor / 100.0);
        summary.put("totalCoinsCirculation", totalCoinsCirculation);
        summary.put("totalCatalogItems", itemRepository.count());
        summary.put("refundCases", refundCaseRepository.count());
        summary.put("refundPendingOrders", refundPending);
        summary.put("pendingOutboxEvents", outboxService.countPending());
        summary.put("asOf", Instant.now());
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/orders")
    @Operation(summary = "Admin top-up orders oversight")
    public ResponseEntity<List<PaymentOrder>> getOrders() {
        return ResponseEntity.ok(orderRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/players")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Admin player account oversight")
    public ResponseEntity<List<User>> getPlayers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Paginated administrative audit trail")
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<AuditLog> result = auditService.getAuditLogs(page, size);
        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        ));
    }
}
