package com.railbound.portal;

import com.railbound.portal.assets.LocalMediaStorage;
import com.railbound.portal.assets.UploadService;
import com.railbound.portal.common.InvalidStateException;
import com.railbound.portal.flags.FeatureFlagService;
import com.railbound.portal.identity.NakamaTokenVerifier;
import com.railbound.portal.identity.User;
import com.railbound.portal.identity.UserRepository;
import com.railbound.portal.notifications.Notification;
import com.railbound.portal.notifications.NotificationService;
import com.railbound.portal.outbox.OutboxRelay;
import com.railbound.portal.outbox.OutboxService;
import com.railbound.portal.releases.ContentRelease;
import com.railbound.portal.releases.ItemVersion;
import com.railbound.portal.releases.ReleaseService;
import com.railbound.portal.shop.ItemCategory;
import com.railbound.portal.shop.PublicationStatus;
import com.railbound.portal.shop.ShopItem;
import com.railbound.portal.shop.ShopItemRepository;
import com.railbound.portal.wallet.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration coverage for the capabilities added after the payment milestone:
 * content release pipeline, transactional outbox, notification inbox, feature
 * flags, staged uploads and Nakama token exchange.
 */
@SpringBootTest
class PortalCompletionTests {

    @Autowired
    private ReleaseService releaseService;

    @Autowired
    private ShopItemRepository itemRepository;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private FeatureFlagService featureFlagService;

    @Autowired
    private UploadService uploadService;

    @Autowired
    private LocalMediaStorage mediaStorage;

    @Autowired
    private NakamaTokenVerifier nakamaTokenVerifier;

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private ShopItem createItem(String sku) {
        ShopItem item = new ShopItem(
                sku,
                ItemCategory.OPERATOR_SKIN,
                "Test Skin " + sku,
                "Integration test item",
                100,
                PublicationStatus.PUBLISHED,
                Map.of("minimumClientVersion", "1.0.0"),
                Map.of(),
                null
        );
        return itemRepository.save(item);
    }

    @Test
    @DisplayName("Release pipeline: validation gates publication and serves a real manifest")
    void releasePipelinePublishesOnlyValidatedContent() {
        String sku = "test_skin_" + uniqueSuffix();
        createItem(sku);

        // Publish gate is closed until a validated content version exists.
        assertFalse(releaseService.hasPublishableVersion(sku));

        ItemVersion version = releaseService.registerVersion(sku, Map.of(
                "version", "1.0.0",
                "bundleKey", "bundles/test/" + sku + ".bundle",
                "sha256", "a".repeat(64),
                "sizeBytes", 4096,
                "minimumClientVersion", "1.0.0"
        ), "test-content-manager");

        assertEquals(ItemVersion.VALIDATION_VALID, version.getValidationStatus());
        assertTrue(releaseService.hasPublishableVersion(sku));

        ContentRelease draft = releaseService.createRelease(Map.of(
                "version", "9.9." + (Math.abs(sku.hashCode()) % 900 + 100),
                "items", List.of(Map.of("itemSku", sku, "version", "1.0.0"))
        ), "test-content-manager");
        assertEquals(ContentRelease.STATUS_DRAFT, draft.getStatus());

        ContentRelease approved = releaseService.approveRelease(draft.getReleaseId(), "test-admin");
        assertEquals(ContentRelease.STATUS_APPROVED, approved.getStatus());

        ContentRelease published = releaseService.publishRelease(draft.getReleaseId(), "test-admin");
        assertEquals(ContentRelease.STATUS_PUBLISHED, published.getStatus());

        ShopItem reloaded = itemRepository.findBySku(sku).orElseThrow();
        assertEquals("1.0.0", reloaded.getCurrentContentVersion());

        Map<String, Object> manifest = releaseService.getManifest();
        assertEquals(published.getVersion(), manifest.get("version"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bundles = (List<Map<String, Object>>) manifest.get("bundles");
        assertTrue(bundles.stream().anyMatch(b -> sku.equals(b.get("itemSku"))),
                "Manifest must contain the published item");
    }

    @Test
    @DisplayName("Release validation rejects a malformed checksum and blocks approval")
    void invalidChecksumBlocksApproval() {
        String sku = "test_bad_" + uniqueSuffix();
        createItem(sku);

        ItemVersion invalid = releaseService.registerVersion(sku, Map.of(
                "version", "1.0.0",
                "bundleKey", "bundles/test/bad.bundle",
                "sha256", "not-a-checksum",
                "sizeBytes", 0
        ), "test-content-manager");

        assertEquals(ItemVersion.VALIDATION_INVALID, invalid.getValidationStatus());
        assertNotNull(invalid.getValidationMessage());
        assertFalse(releaseService.hasPublishableVersion(sku));

        // A draft may reference the version, but approval must refuse it.
        ContentRelease draft = releaseService.createRelease(Map.of(
                "version", "1.2.3",
                "items", List.of(Map.of("itemSku", sku, "version", "1.0.0"))
        ), "test-content-manager");

        assertThrows(InvalidStateException.class,
                () -> releaseService.approveRelease(draft.getReleaseId(), "test-admin"));
    }

    @Test
    @DisplayName("Outbox relays events into an idempotent player notification")
    void outboxRelayProjectsNotifications() {
        String userId = "usr_outbox_" + uniqueSuffix();
        userRepository.save(new User(userId, "nakama_" + userId, "Outbox Tester", "PLAYER"));
        walletService.getOrCreateWallet(userId);

        outboxService.enqueue(OutboxService.WALLET_CREDITED, "payment_order", "ord_outbox_test",
                Map.of("userId", userId, "coins", 1150, "orderId", "ord_outbox_test"));

        outboxRelay.relayOnce();

        List<Notification> notifications = notificationService.getMyNotifications(userId);
        assertEquals(1, notifications.size());
        assertEquals(OutboxService.WALLET_CREDITED, notifications.get(0).getType());
        assertEquals(1, notificationService.getUnreadCount(userId));

        // A second relay pass must not duplicate the inbox entry.
        outboxRelay.relayOnce();
        assertEquals(1, notificationService.getMyNotifications(userId).size());

        Notification read = notificationService.markRead(userId, notifications.get(0).getNotificationId());
        assertNotNull(read.getReadAt());
        assertEquals(0, notificationService.getUnreadCount(userId));
    }

    @Test
    @DisplayName("Feature flags drive the public game config")
    void featureFlagsDriveGameConfig() {
        assertTrue((Boolean) featureFlagService.getPublicConfig().get("realCoinsEnabled"));
        assertFalse((Boolean) featureFlagService.getPublicConfig().get("gameCoinsEnabled"));

        try {
            featureFlagService.setEnabled("shopEnabled", false, "test-admin");
            assertFalse(featureFlagService.isEnabled("shopEnabled", true));
            assertEquals(false, featureFlagService.getPublicConfig().get("shopEnabled"));
        } finally {
            featureFlagService.setEnabled("shopEnabled", true, "test-admin");
        }
        assertTrue(featureFlagService.isEnabled("shopEnabled", false));
    }

    @Test
    @DisplayName("Upload grants enforce type, size, single use and path containment")
    void uploadGrantsAreConstrained() {
        assertThrows(InvalidStateException.class,
                () -> uploadService.createGrant("test-admin", "text/plain", 10));

        Map<String, Object> grant = uploadService.createGrant("test-admin", "image/png", 1024);
        String token = String.valueOf(grant.get("uploadUrl")).substring("/api/v1/uploads/".length());

        UploadService.UploadResult result = uploadService.acceptUpload(token, new byte[]{1, 2, 3, 4});
        assertTrue(mediaStorage.exists(result.objectKey()));
        assertEquals(64, result.sha256().length());
        assertTrue(result.publicUrl().startsWith("/api/v1/media/"));

        // Grants are single use.
        assertThrows(InvalidStateException.class,
                () -> uploadService.acceptUpload(token, new byte[]{1}));

        // Server-generated keys cannot escape the storage root.
        assertThrows(IllegalArgumentException.class, () -> mediaStorage.resolveSafe("../escape.txt"));
    }

    @Test
    @DisplayName("Nakama session tokens are verified against the shared server key")
    void nakamaTokensAreVerified() {
        String nakamaUserId = UUID.randomUUID().toString();
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"usid\":\"" + nakamaUserId
                + "\",\"username\":\"GhostActual\",\"exp\":" + (Instant.now().getEpochSecond() + 300) + "}");

        String valid = header + "." + payload + "." + hmacSha256(header + "." + payload, "defaultkey");
        NakamaTokenVerifier.NakamaIdentity identity = nakamaTokenVerifier.verify(valid).orElseThrow();
        assertEquals(nakamaUserId, identity.nakamaUserId());
        assertEquals("GhostActual", identity.displayName());

        String forged = header + "." + payload + "." + hmacSha256(header + "." + payload, "wrong-key");
        assertTrue(nakamaTokenVerifier.verify(forged).isEmpty());

        String expiredPayload = base64Url("{\"usid\":\"" + nakamaUserId
                + "\",\"exp\":" + (Instant.now().getEpochSecond() - 60) + "}");
        String expired = header + "." + expiredPayload + "."
                + hmacSha256(header + "." + expiredPayload, "defaultkey");
        assertTrue(nakamaTokenVerifier.verify(expired).isEmpty());
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha256(String value, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
