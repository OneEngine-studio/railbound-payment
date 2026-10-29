package com.railbound.portal.wallet;

import com.railbound.portal.ledger.WalletLedgerEntry;
import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/wallet")
@Tag(name = "Wallet", description = "Player wallet and ledger queries")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated player Real Coin balance")
    public ResponseEntity<Map<String, Object>> getMyWallet() {
        String userId = SecurityUtils.getCurrentUserId();
        Wallet wallet = walletService.getOrCreateWallet(userId);

        return ResponseEntity.ok(Map.of(
                "userId", wallet.getUserId(),
                "realCoins", wallet.getRealCoins(),
                "gameCoins", wallet.getGameCoins(),
                "updatedAt", wallet.getUpdatedAt()
        ));
    }

    @GetMapping("/me/ledger")
    @Operation(summary = "Get paginated wallet transaction ledger")
    public ResponseEntity<Map<String, Object>> getMyLedger(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        Page<WalletLedgerEntry> entries = walletService.getLedgerEntries(userId, PageRequest.of(page, size));

        return ResponseEntity.ok(Map.of(
                "items", entries.getContent(),
                "page", entries.getNumber(),
                "totalElements", entries.getTotalElements(),
                "totalPages", entries.getTotalPages()
        ));
    }
}
