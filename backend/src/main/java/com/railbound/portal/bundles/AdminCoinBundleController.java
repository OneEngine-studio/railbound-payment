package com.railbound.portal.bundles;

import com.railbound.portal.audit.AuditService;
import com.railbound.portal.common.DuplicateResourceException;
import com.railbound.portal.common.ResourceNotFoundException;
import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Coin bundle administration.
 *
 * <p>Editing a bundle never rewrites history: every {@code payment_order} keeps
 * its own immutable coin/amount snapshot, so a price change only affects future
 * checkouts.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/coin-bundles")
@Tag(name = "Admin Bundles", description = "Pricing and availability of Real Coin bundles")
public class AdminCoinBundleController {

    private final CoinBundleRepository bundleRepository;
    private final AuditService auditService;

    public AdminCoinBundleController(CoinBundleRepository bundleRepository, AuditService auditService) {
        this.bundleRepository = bundleRepository;
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "List all bundles including inactive ones")
    public ResponseEntity<List<CoinBundle>> list() {
        return ResponseEntity.ok(bundleRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Create a Real Coin bundle")
    public ResponseEntity<CoinBundle> create(@RequestBody CoinBundle bundle) {
        if (bundle.getSku() == null || bundle.getSku().isBlank()) {
            throw new IllegalArgumentException("sku is required");
        }
        if (bundleRepository.findBySku(bundle.getSku()).isPresent()) {
            throw new DuplicateResourceException("Bundle already exists: " + bundle.getSku());
        }
        if (bundle.getCurrency() == null || bundle.getCurrency().isBlank()) {
            bundle.setCurrency("USD");
        }
        if (bundle.getTotalCoins() <= 0) {
            bundle.setTotalCoins(bundle.getBaseCoins() + bundle.getBonusCoins());
        }
        if (bundle.getFiatAmountMinor() <= 0) {
            throw new IllegalArgumentException("fiatAmountMinor must be positive");
        }

        CoinBundle saved = bundleRepository.save(bundle);
        auditService.record("BUNDLE_CREATED", "coin_bundle", saved.getSku(), Map.of(),
                Map.of("totalCoins", saved.getTotalCoins(),
                        "fiatAmountMinor", saved.getFiatAmountMinor()));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/{sku}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Update bundle pricing, coins or availability")
    public ResponseEntity<CoinBundle> update(@PathVariable String sku, @RequestBody Map<String, Object> updates) {
        CoinBundle bundle = bundleRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Bundle not found: " + sku));

        Map<String, Object> before = Map.of(
                "baseCoins", bundle.getBaseCoins(),
                "bonusCoins", bundle.getBonusCoins(),
                "fiatAmountMinor", bundle.getFiatAmountMinor(),
                "active", bundle.isActive()
        );

        if (updates.containsKey("title")) bundle.setTitle(String.valueOf(updates.get("title")));
        if (updates.containsKey("baseCoins")) bundle.setBaseCoins(number(updates.get("baseCoins")));
        if (updates.containsKey("bonusCoins")) bundle.setBonusCoins(number(updates.get("bonusCoins")));
        if (updates.containsKey("fiatAmountMinor")) {
            long amount = number(updates.get("fiatAmountMinor"));
            if (amount <= 0) {
                throw new IllegalArgumentException("fiatAmountMinor must be positive");
            }
            bundle.setFiatAmountMinor(amount);
        }
        if (updates.containsKey("badgeText")) bundle.setBadgeText(String.valueOf(updates.get("badgeText")));
        if (updates.containsKey("sortOrder")) bundle.setSortOrder((int) number(updates.get("sortOrder")));
        if (updates.get("active") instanceof Boolean active) bundle.setActive(active);
        bundle.setTotalCoins(bundle.getBaseCoins() + bundle.getBonusCoins());

        CoinBundle saved = bundleRepository.save(bundle);
        auditService.record("BUNDLE_UPDATED", "coin_bundle", sku, before,
                Map.of("baseCoins", saved.getBaseCoins(),
                        "bonusCoins", saved.getBonusCoins(),
                        "fiatAmountMinor", saved.getFiatAmountMinor(),
                        "active", saved.isActive(),
                        "updatedAt", Instant.now().toString()));
        return ResponseEntity.ok(saved);
    }

    private static long number(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
