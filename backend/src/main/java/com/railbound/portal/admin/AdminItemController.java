package com.railbound.portal.admin;

import com.railbound.portal.audit.AuditService;
import com.railbound.portal.common.DuplicateResourceException;
import com.railbound.portal.common.InvalidStateException;
import com.railbound.portal.common.ResourceNotFoundException;
import com.railbound.portal.releases.ItemVersion;
import com.railbound.portal.releases.ReleaseService;
import com.railbound.portal.security.SecurityUtils;
import com.railbound.portal.shop.ItemCategory;
import com.railbound.portal.shop.PublicationStatus;
import com.railbound.portal.shop.ShopItem;
import com.railbound.portal.shop.ShopItemRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog authoring.
 *
 * <p>Publishing is gated on validated content: an item cannot go live unless at
 * least one registered {@code item_version} passed bundle/checksum validation.
 * That is the difference between "metadata exists" and "a Unity client can
 * actually load this asset".</p>
 */
@RestController
@RequestMapping("/api/v1/admin/items")
@Tag(name = "Admin Items", description = "Catalog authoring, publishing and archiving")
public class AdminItemController {

    private final ShopItemRepository itemRepository;
    private final ReleaseService releaseService;
    private final AuditService auditService;

    public AdminItemController(ShopItemRepository itemRepository,
                               ReleaseService releaseService,
                               AuditService auditService) {
        this.itemRepository = itemRepository;
        this.releaseService = releaseService;
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Admin list all catalog items including drafts")
    public ResponseEntity<List<ShopItem>> getAllItems() {
        return ResponseEntity.ok(itemRepository.findAll());
    }

    @GetMapping("/{sku}")
    @Operation(summary = "Admin inspect item draft by SKU")
    public ResponseEntity<Map<String, Object>> getItem(@PathVariable String sku) {
        ShopItem item = requireItem(sku);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("item", item);
        response.put("versions", releaseService.listVersions(sku));
        response.put("publishable", releaseService.hasPublishableVersion(sku));
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Admin create draft item")
    public ResponseEntity<ShopItem> createItem(@RequestBody ShopItem item) {
        if (item.getSku() == null || item.getSku().isBlank()) {
            throw new InvalidStateException("sku is required");
        }
        if (itemRepository.findBySku(item.getSku()).isPresent()) {
            throw new DuplicateResourceException("Item with SKU already exists: " + item.getSku());
        }
        item.setPublicationStatus(PublicationStatus.DRAFT);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        ShopItem saved = itemRepository.save(item);

        auditService.record("ITEM_CREATED", "shop_item", saved.getSku(), Map.of(),
                Map.of("title", String.valueOf(saved.getTitle()), "price", saved.getRealCoinPrice()));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/{sku}")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Admin update draft metadata")
    public ResponseEntity<ShopItem> updateItem(@PathVariable String sku, @RequestBody Map<String, Object> updates) {
        ShopItem item = requireItem(sku);
        Map<String, Object> before = Map.of(
                "title", String.valueOf(item.getTitle()),
                "price", item.getRealCoinPrice(),
                "status", String.valueOf(item.getPublicationStatus())
        );

        if (updates.containsKey("title")) item.setTitle((String) updates.get("title"));
        if (updates.containsKey("description")) item.setDescription((String) updates.get("description"));
        if (updates.containsKey("realCoinPrice")) {
            item.setRealCoinPrice(((Number) updates.get("realCoinPrice")).longValue());
        }
        if (updates.containsKey("category")) {
            item.setCategory(ItemCategory.valueOf(String.valueOf(updates.get("category"))));
        }
        if (updates.containsKey("compatibility") && updates.get("compatibility") instanceof Map<?, ?> compatibility) {
            item.setCompatibility(castMap(compatibility));
        }
        if (updates.containsKey("previewMedia") && updates.get("previewMedia") instanceof Map<?, ?> preview) {
            item.setPreviewMedia(castStringMap(preview));
        }
        item.setUpdatedAt(Instant.now());
        ShopItem saved = itemRepository.save(item);

        auditService.record("ITEM_UPDATED", "shop_item", sku, before,
                Map.of("title", String.valueOf(saved.getTitle()), "price", saved.getRealCoinPrice()));
        return ResponseEntity.ok(saved);
    }

    // ---------------------------------------------------------------------
    // Content versions
    // ---------------------------------------------------------------------

    @GetMapping("/{sku}/versions")
    @Operation(summary = "List registered content versions for an item")
    public ResponseEntity<List<ItemVersion>> listVersions(@PathVariable String sku) {
        requireItem(sku);
        return ResponseEntity.ok(releaseService.listVersions(sku));
    }

    @PostMapping("/{sku}/versions")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Register a content version with bundle key, checksum and size")
    public ResponseEntity<ItemVersion> registerVersion(@PathVariable String sku,
                                                       @RequestBody Map<String, Object> request) {
        ItemVersion version = releaseService.registerVersion(sku, request, SecurityUtils.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(version);
    }

    @PostMapping("/{sku}/versions/{version}/validate")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Re-run compatibility validation for one content version")
    public ResponseEntity<ItemVersion> validateVersion(@PathVariable String sku, @PathVariable String version) {
        return ResponseEntity.ok(releaseService.validateVersion(sku, version, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/{sku}/validate")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Validate the newest content version of an item")
    public ResponseEntity<ItemVersion> validateLatest(@PathVariable String sku) {
        List<ItemVersion> versions = releaseService.listVersions(sku);
        if (versions.isEmpty()) {
            throw new InvalidStateException("No content version registered for " + sku);
        }
        ItemVersion latest = versions.get(0);
        return ResponseEntity.ok(releaseService.validateVersion(sku, latest.getVersion(),
                SecurityUtils.getCurrentUserId()));
    }

    // ---------------------------------------------------------------------
    // Publication
    // ---------------------------------------------------------------------

    @PostMapping("/{sku}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Admin publish approved item to live store")
    public ResponseEntity<ShopItem> publishItem(@PathVariable String sku) {
        ShopItem item = requireItem(sku);
        if (!releaseService.hasPublishableVersion(sku)) {
            throw new InvalidStateException(
                    "Cannot publish " + sku + ": no validated content version (bundle key + SHA-256 + size) exists");
        }
        releaseService.latestValidVersion(sku)
                .ifPresent(version -> item.setCurrentContentVersion(version.getVersion()));

        String previous = String.valueOf(item.getPublicationStatus());
        item.setPublicationStatus(PublicationStatus.PUBLISHED);
        item.setUpdatedAt(Instant.now());
        ShopItem saved = itemRepository.save(item);

        auditService.record("ITEM_PUBLISHED", "shop_item", sku,
                Map.of("status", previous),
                Map.of("status", saved.getPublicationStatus(),
                        "contentVersion", String.valueOf(saved.getCurrentContentVersion())));
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{sku}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Admin retire item from live store (preserves player ownership)")
    public ResponseEntity<ShopItem> archiveItem(@PathVariable String sku) {
        ShopItem item = requireItem(sku);
        String previous = String.valueOf(item.getPublicationStatus());
        item.setPublicationStatus(PublicationStatus.ARCHIVED);
        item.setUpdatedAt(Instant.now());
        ShopItem saved = itemRepository.save(item);

        auditService.record("ITEM_ARCHIVED", "shop_item", sku,
                Map.of("status", previous), Map.of("status", saved.getPublicationStatus()));
        return ResponseEntity.ok(saved);
    }

    private ShopItem requireItem(String sku) {
        return itemRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + sku));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Map<String, String> castStringMap(Map<?, ?> source) {
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), String.valueOf(value)));
        return result;
    }
}
