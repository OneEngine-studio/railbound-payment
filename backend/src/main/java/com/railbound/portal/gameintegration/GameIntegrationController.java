package com.railbound.portal.gameintegration;

import com.railbound.portal.announcements.Announcement;
import com.railbound.portal.announcements.AnnouncementService;
import com.railbound.portal.flags.FeatureFlagService;
import com.railbound.portal.identity.User;
import com.railbound.portal.identity.UserRepository;
import com.railbound.portal.inventory.InventoryEntitlement;
import com.railbound.portal.inventory.InventoryService;
import com.railbound.portal.notifications.Notification;
import com.railbound.portal.notifications.NotificationService;
import com.railbound.portal.releases.ContentRelease;
import com.railbound.portal.releases.ReleaseService;
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
import java.util.LinkedHashMap;
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
    private final NotificationService notificationService;
    private final ReleaseService releaseService;
    private final FeatureFlagService featureFlagService;
    private final UserRepository userRepository;
    private final String internalServiceKey;

    public GameIntegrationController(
            WalletService walletService,
            InventoryService inventoryService,
            ShopService shopService,
            AnnouncementService announcementService,
            NotificationService notificationService,
            ReleaseService releaseService,
            FeatureFlagService featureFlagService,
            UserRepository userRepository,
            @Value("${app.internal.service-key}") String internalServiceKey) {
        this.walletService = walletService;
        this.inventoryService = inventoryService;
        this.shopService = shopService;
        this.announcementService = announcementService;
        this.notificationService = notificationService;
        this.releaseService = releaseService;
        this.featureFlagService = featureFlagService;
        this.userRepository = userRepository;
        this.internalServiceKey = internalServiceKey;
    }

    @GetMapping("/game/sync")
    @Operation(summary = "In-game client synchronization cursor (wallet + inventory + news + notifications)")
    public ResponseEntity<Map<String, Object>> gameSync() {
        String userId = SecurityUtils.getCurrentUserId();
        Wallet wallet = walletService.getOrCreateWallet(userId);
        List<InventoryEntitlement> entitlements = inventoryService.getMyEntitlements(userId);
        List<Announcement> news = announcementService.getPublishedNews();
        String latestNewsId = news.isEmpty() ? null : news.get(0).getAnnouncementId();

        List<Notification> notifications = notificationService.getMyNotifications(userId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", userId);
        response.put("realCoins", wallet.getRealCoins());
        response.put("gameCoins", wallet.getGameCoins());
        response.put("entitlements", entitlements);
        response.put("latestNewsId", latestNewsId != null ? latestNewsId : "");
        response.put("notifications", notifications);
        response.put("unreadNotifications", notificationService.getUnreadCount(userId));
        response.put("contentVersion", releaseService.getLatestPublished()
                .map(ContentRelease::getVersion)
                .orElse("0.0.0"));
        response.put("serverTime", Instant.now());
        response.put("syncedAt", Instant.now());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/game/catalog")
    @Operation(summary = "Get published items formatted for Unity engine consumption")
    public ResponseEntity<List<ShopItem>> getGameCatalog() {
        return ResponseEntity.ok(shopService.getPublishedItems(null));
    }

    @GetMapping("/game/content-manifest")
    @Operation(summary = "Versioned content manifest with bundle keys, SHA-256 hashes and sizes")
    public ResponseEntity<Map<String, Object>> getContentManifest() {
        return ResponseEntity.ok(releaseService.getManifest());
    }

    @GetMapping("/game/releases/latest")
    @Operation(summary = "Latest published client content release")
    public ResponseEntity<Map<String, Object>> getLatestRelease() {
        return releaseService.getLatestPublished()
                .map(release -> ResponseEntity.ok(Map.<String, Object>of(
                        "releaseId", release.getReleaseId(),
                        "version", release.getVersion(),
                        "manifestVersion", release.getManifestVersion(),
                        "status", release.getStatus(),
                        "targetClientVersions", release.getTargetClientVersions(),
                        "itemCount", release.getItems().size(),
                        "publishedAt", release.getPublishedAt() == null ? Instant.now() : release.getPublishedAt()
                )))
                .orElseGet(() -> ResponseEntity.ok(Map.of(
                        "releaseId", "",
                        "version", "0.0.0",
                        "status", "NONE",
                        "itemCount", 0
                )));
    }

    @GetMapping("/game/config")
    @Operation(summary = "Safe public runtime game configuration and feature flags")
    public ResponseEntity<Map<String, Object>> getGameConfig() {
        return ResponseEntity.ok(featureFlagService.getPublicConfig());
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
