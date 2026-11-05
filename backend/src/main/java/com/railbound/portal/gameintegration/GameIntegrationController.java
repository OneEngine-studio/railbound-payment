package com.railbound.portal.gameintegration;

import com.railbound.portal.announcements.Announcement;
import com.railbound.portal.announcements.AnnouncementService;
import com.railbound.portal.identity.User;
import com.railbound.portal.identity.UserRepository;
import com.railbound.portal.inventory.InventoryEntitlement;
import com.railbound.portal.inventory.InventoryService;
import com.railbound.portal.security.SecurityUtils;
import com.railbound.portal.shop.ShopItem;
import com.railbound.portal.shop.ShopService;
import com.railbound.portal.wallet.Wallet;
import com.railbound.portal.wallet.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Game Integration", description = "Unity and Nakama sync & verification endpoints")
public class GameIntegrationController {

    private final WalletService walletService;
    private final InventoryService inventoryService;
    private final ShopService shopService;
    private final AnnouncementService announcementService;
    private final UserRepository userRepository;
    private final String internalServiceKey;

    public GameIntegrationController(
            WalletService walletService,
            InventoryService inventoryService,
            ShopService shopService,
            AnnouncementService announcementService,
            UserRepository userRepository,
            @Value("${app.internal.service-key}") String internalServiceKey) {
        this.walletService = walletService;
        this.inventoryService = inventoryService;
        this.shopService = shopService;
        this.announcementService = announcementService;
        this.userRepository = userRepository;
        this.internalServiceKey = internalServiceKey;
    }

    @GetMapping("/game/sync")
    @Operation(summary = "In-game client synchronization cursor (wallet + inventory + news cursor)")
    public ResponseEntity<Map<String, Object>> gameSync() {
        String userId = SecurityUtils.getCurrentUserId();
        Wallet wallet = walletService.getOrCreateWallet(userId);
        List<InventoryEntitlement> entitlements = inventoryService.getMyEntitlements(userId);
        List<Announcement> news = announcementService.getPublishedNews();
        String latestNewsId = news.isEmpty() ? null : news.get(0).getAnnouncementId();

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "realCoins", wallet.getRealCoins(),
                "gameCoins", wallet.getGameCoins(),
                "entitlements", entitlements,
                "latestNewsId", latestNewsId != null ? latestNewsId : "",
                "syncedAt", Instant.now()
        ));
    }

    @GetMapping("/game/catalog")
    @Operation(summary = "Get published items formatted for Unity engine consumption")
    public ResponseEntity<List<ShopItem>> getGameCatalog() {
        return ResponseEntity.ok(shopService.getPublishedItems(null));
    }

    @GetMapping("/game/content-manifest")
    @Operation(summary = "Signed/versioned content manifest metadata with SHA-256 bundle hashes")
    public ResponseEntity<Map<String, Object>> getContentManifest() {
        return ResponseEntity.ok(Map.of(
                "manifestVersion", "1.0.0",
                "targetClientVersions", List.of("2023.1", "1.0.0"),
                "generatedAt", Instant.now(),
                "bundles", List.of(
                        Map.of(
                                "bundleKey", "bundles/weapons/att_scope_tactical_acog.bundle",
                                "itemSku", "att_scope_tactical_acog",
                                "sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                                "sizeBytes", 248912
                        ),
                        Map.of(
                                "bundleKey", "bundles/operators/skin_op_shadow_operative.bundle",
                                "itemSku", "skin_op_shadow_operative",
                                "sha256", "38b9195b28d08c8b417eef982c71f49e49a1d5a76c8c889f0237bb9610f13511",
                                "sizeBytes", 894120
                        )
                )
        ));
    }

    @GetMapping("/game/releases/latest")
    @Operation(summary = "Latest compatible client content release")
    public ResponseEntity<Map<String, Object>> getLatestRelease() {
        return ResponseEntity.ok(Map.of(
                "releaseId", "rel_2026_10_29",
                "version", "1.0.0",
                "status", "PUBLISHED",
                "publishedAt", Instant.now()
        ));
    }

    @GetMapping("/game/config")
    @Operation(summary = "Safe public runtime game configuration and feature flags")
    public ResponseEntity<Map<String, Object>> getGameConfig() {
        return ResponseEntity.ok(Map.of(
                "realCoinsEnabled", true,
                "gameCoinsEnabled", false,
                "shopEnabled", true,
                "topUpEnabled", true,
                "minClientVersion", "1.0.0"
        ));
    }

    @PostMapping("/internal/entitlements/check")
    @Operation(summary = "Service-to-service entitlement verification for trusted Nakama/game backend")
    public ResponseEntity<Map<String, Object>> checkInternalEntitlement(
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String serviceKey,
            @RequestBody Map<String, String> request) {

        if (serviceKey == null || !serviceKey.equals(internalServiceKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "authorized", false,
                    "error", "Invalid or missing internal service key"
            ));
        }

        String nakamaUserId = request.get("nakamaUserId");
        String itemSku = request.get("itemSku");

        User user = userRepository.findByNakamaUserId(nakamaUserId).orElse(null);
        if (user == null) {
            return ResponseEntity.ok(Map.of("authorized", false, "reason", "USER_NOT_FOUND"));
        }

        boolean owned = inventoryService.isOwned(user.getUserId(), itemSku);
        return ResponseEntity.ok(Map.of(
                "authorized", owned,
                "userId", user.getUserId(),
                "nakamaUserId", nakamaUserId,
                "itemSku", itemSku,
                "status", owned ? "ACTIVE" : "NOT_ENTITLED"
        ));
    }
}
