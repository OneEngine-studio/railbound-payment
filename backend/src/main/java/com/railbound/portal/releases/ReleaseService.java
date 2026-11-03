package com.railbound.portal.releases;

import com.railbound.portal.audit.AuditService;
import com.railbound.portal.common.DuplicateResourceException;
import com.railbound.portal.common.InvalidStateException;
import com.railbound.portal.common.ResourceNotFoundException;
import com.railbound.portal.outbox.OutboxService;
import com.railbound.portal.shop.PublicationStatus;
import com.railbound.portal.shop.ShopItem;
import com.railbound.portal.shop.ShopItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Content release pipeline: register version -> validate -> approve -> publish.
 *
 * <p>Publishing is the only step that changes what clients are told to load, and
 * it refuses to run unless every referenced item version has a bundle key,
 * checksum and size. This is what prevents the portal from advertising an item
 * as playable when no Unity content exists for it.</p>
 */
@Service
public class ReleaseService {

    private static final Logger log = LoggerFactory.getLogger(ReleaseService.class);
    private static final Pattern SHA256 = Pattern.compile("^[a-fA-F0-9]{64}$");
    private static final Pattern SEMVER = Pattern.compile("^\\d+\\.\\d+\\.\\d+([-.+][A-Za-z0-9.-]+)?$");

    private final ItemVersionRepository versionRepository;
    private final ContentReleaseRepository releaseRepository;
    private final ShopItemRepository itemRepository;
    private final AuditService auditService;
    private final OutboxService outboxService;
    private final String manifestVersion;

    public ReleaseService(
            ItemVersionRepository versionRepository,
            ContentReleaseRepository releaseRepository,
            ShopItemRepository itemRepository,
            AuditService auditService,
            OutboxService outboxService,
            @Value("${app.content.manifest-version:1.0.0}") String manifestVersion) {
        this.versionRepository = versionRepository;
        this.releaseRepository = releaseRepository;
        this.itemRepository = itemRepository;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.manifestVersion = manifestVersion;
    }

    // ---------------------------------------------------------------------
    // Item versions
    // ---------------------------------------------------------------------

    public ItemVersion registerVersion(String sku, Map<String, Object> request, String actorId) {
        ShopItem item = itemRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + sku));

        String version = string(request.get("version"));
        if (version == null || !SEMVER.matcher(version).matches()) {
            throw new InvalidStateException("version must be semver, e.g. 1.0.0");
        }
        if (versionRepository.existsByItemSkuAndVersion(sku, version)) {
            throw new DuplicateResourceException("Version already registered for " + sku + ": " + version);
        }

        ItemVersion entity = new ItemVersion(
                item.getSku(),
                version,
                firstNonBlank(string(request.get("minimumClientVersion")),
                        string(item.getCompatibility() == null ? null : item.getCompatibility().get("minimumClientVersion")),
                        "1.0.0"),
                string(request.get("bundleKey")),
                string(request.get("sha256")),
                longValue(request.get("sizeBytes")),
                actorId
        );
        applyValidation(entity);

        ItemVersion saved = versionRepository.save(entity);
        auditService.recordWithActor(actorId, "CONTENT_MANAGER", "ITEM_VERSION_REGISTERED",
                "item_version", sku + "@" + version, Map.of(),
                Map.of("validationStatus", saved.getValidationStatus()));
        log.info("Registered item version {}@{} as {}", sku, version, saved.getValidationStatus());
        return saved;
    }

    public List<ItemVersion> listVersions(String sku) {
        return versionRepository.findByItemSkuOrderByCreatedAtDesc(sku);
    }

    public ItemVersion validateVersion(String sku, String version, String actorId) {
        ItemVersion entity = versionRepository.findByItemSkuAndVersion(sku, version)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + sku + "@" + version));

        String previous = entity.getValidationStatus();
        applyValidation(entity);
        ItemVersion saved = versionRepository.save(entity);

        auditService.recordWithActor(actorId, "CONTENT_MANAGER", "ITEM_VERSION_VALIDATED",
                "item_version", sku + "@" + version,
                Map.of("validationStatus", previous),
                Map.of("validationStatus", saved.getValidationStatus()));
        return saved;
    }

    /** Publish gate used by the admin item controller. */
    public boolean hasPublishableVersion(String sku) {
        return versionRepository
                .findTopByItemSkuAndValidationStatusOrderByCreatedAtDesc(sku, ItemVersion.VALIDATION_VALID)
                .isPresent();
    }

    public Optional<ItemVersion> latestValidVersion(String sku) {
        return versionRepository.findTopByItemSkuAndValidationStatusOrderByCreatedAtDesc(
                sku, ItemVersion.VALIDATION_VALID);
    }

    private void applyValidation(ItemVersion entity) {
        List<String> problems = new ArrayList<>();
        if (isBlank(entity.getBundleKey())) {
            problems.add("bundleKey is required");
        }
        if (isBlank(entity.getMinimumClientVersion())) {
            problems.add("minimumClientVersion is required");
        }
        if (isBlank(entity.getSha256()) || !SHA256.matcher(entity.getSha256()).matches()) {
            problems.add("sha256 must be 64 hex characters");
        }
        if (entity.getSizeBytes() <= 0) {
            problems.add("sizeBytes must be positive");
        }

        entity.setValidationStatus(problems.isEmpty()
                ? ItemVersion.VALIDATION_VALID
                : ItemVersion.VALIDATION_INVALID);
        entity.setValidationMessage(problems.isEmpty() ? null : String.join("; ", problems));
    }

    // ---------------------------------------------------------------------
    // Releases
    // ---------------------------------------------------------------------

    public ContentRelease createRelease(Map<String, Object> request, String actorId) {
        String version = string(request.get("version"));
        if (version == null || !SEMVER.matcher(version).matches()) {
            throw new InvalidStateException("version must be semver, e.g. 1.1.0");
        }

        List<ContentRelease.ReleaseItemRef> refs = new ArrayList<>();
        Object rawItems = request.get("items");

        if (rawItems instanceof List<?> list && !list.isEmpty()) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> map)) {
                    throw new InvalidStateException("items entries must be {itemSku, version} objects");
                }
                String itemSku = string(map.get("itemSku"));
                String itemVersion = string(map.get("version"));
                ItemVersion resolved = resolveVersion(itemSku, itemVersion);
                refs.add(new ContentRelease.ReleaseItemRef(itemSku, resolved.getVersion()));
            }
        } else {
            // Default: snapshot the latest valid version of every published item.
            for (ShopItem item : itemRepository.findByPublicationStatus(PublicationStatus.PUBLISHED)) {
                latestValidVersion(item.getSku()).ifPresent(v ->
                        refs.add(new ContentRelease.ReleaseItemRef(item.getSku(), v.getVersion())));
            }
        }

        if (refs.isEmpty()) {
            throw new InvalidStateException(
                    "A release must contain at least one item version with validated content");
        }

        ContentRelease release = new ContentRelease();
        release.setReleaseId("rel_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        release.setVersion(version);
        release.setManifestVersion(firstNonBlank(string(request.get("manifestVersion")), manifestVersion, "1.0.0"));
        release.setItems(refs);
        release.setTargetClientVersions(stringList(request.get("targetClientVersions"),
                List.of("2023.1", "1.0.0")));
        release.setNotes(string(request.get("notes")));
        release.setCreatedBy(actorId);

        ContentRelease saved = releaseRepository.save(release);
        auditService.recordWithActor(actorId, "CONTENT_MANAGER", "RELEASE_DRAFTED",
                "content_release", saved.getReleaseId(), Map.of(),
                Map.of("version", saved.getVersion(), "items", refs.size()));
        log.info("Drafted content release {} v{} with {} item versions",
                saved.getReleaseId(), saved.getVersion(), refs.size());
        return saved;
    }

    public List<ContentRelease> listReleases() {
        return releaseRepository.findAllByOrderByCreatedAtDesc();
    }

    public ContentRelease getRelease(String releaseId) {
        return releaseRepository.findByReleaseId(releaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Release not found: " + releaseId));
    }

    public ContentRelease approveRelease(String releaseId, String actorId) {
        ContentRelease release = getRelease(releaseId);
        if (!ContentRelease.STATUS_DRAFT.equals(release.getStatus())) {
            throw new InvalidStateException("Only DRAFT releases can be approved (was " + release.getStatus() + ")");
        }
        requireAllVersionsValid(release);

        release.setStatus(ContentRelease.STATUS_APPROVED);
        release.setApprovedBy(actorId);
        release.setApprovedAt(Instant.now());

        ContentRelease saved = releaseRepository.save(release);
        auditService.recordWithActor(actorId, "ADMIN", "RELEASE_APPROVED",
                "content_release", releaseId, Map.of("status", ContentRelease.STATUS_DRAFT),
                Map.of("status", saved.getStatus()));
        return saved;
    }

    @Transactional
    public ContentRelease publishRelease(String releaseId, String actorId) {
        ContentRelease release = getRelease(releaseId);
        if (!ContentRelease.STATUS_APPROVED.equals(release.getStatus())) {
            throw new InvalidStateException("Only APPROVED releases can be published (was " + release.getStatus() + ")");
        }
        requireAllVersionsValid(release);

        // Retire the previously published release first so clients never see two
        // "latest" releases at once.
        releaseRepository.findTopByStatusOrderByPublishedAtDesc(ContentRelease.STATUS_PUBLISHED)
                .ifPresent(previous -> {
                    previous.setStatus(ContentRelease.STATUS_SUPERSEDED);
                    releaseRepository.save(previous);
                });

        for (ContentRelease.ReleaseItemRef ref : release.getItems()) {
            ShopItem item = itemRepository.findBySku(ref.getItemSku())
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + ref.getItemSku()));
            item.setCurrentContentVersion(ref.getVersion());
            item.setUpdatedAt(Instant.now());
            itemRepository.save(item);
        }

        release.setStatus(ContentRelease.STATUS_PUBLISHED);
        release.setPublishedAt(Instant.now());
        ContentRelease saved = releaseRepository.save(release);

        outboxService.enqueue(OutboxService.RELEASE_PUBLISHED, "content_release", releaseId,
                Map.of("releaseId", releaseId, "version", release.getVersion(),
                        "itemSkus", release.getItems().stream().map(ContentRelease.ReleaseItemRef::getItemSku).toList()));

        auditService.recordWithActor(actorId, "ADMIN", "RELEASE_PUBLISHED",
                "content_release", releaseId, Map.of("status", ContentRelease.STATUS_APPROVED),
                Map.of("status", saved.getStatus(), "version", saved.getVersion()));
        log.info("Published content release {} v{}", releaseId, release.getVersion());
        return saved;
    }

    /**
     * Rolls the live manifest back to the previous published release. Player
     * entitlements and wallet state are untouched: only delivery metadata moves.
     */
    public ContentRelease rollbackRelease(String releaseId, String actorId) {
        ContentRelease current = getRelease(releaseId);
        if (!ContentRelease.STATUS_PUBLISHED.equals(current.getStatus())) {
            throw new InvalidStateException("Only the live release can be rolled back");
        }

        ContentRelease previous = releaseRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> ContentRelease.STATUS_SUPERSEDED.equals(r.getStatus()))
                .max(Comparator.comparing(ContentRelease::getPublishedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElseThrow(() -> new InvalidStateException("No previous release is available to roll back to"));

        current.setStatus(ContentRelease.STATUS_ROLLED_BACK);
        releaseRepository.save(current);

        previous.setStatus(ContentRelease.STATUS_PUBLISHED);
        previous.setPublishedAt(Instant.now());
        for (ContentRelease.ReleaseItemRef ref : previous.getItems()) {
            itemRepository.findBySku(ref.getItemSku()).ifPresent(item -> {
                item.setCurrentContentVersion(ref.getVersion());
                item.setUpdatedAt(Instant.now());
                itemRepository.save(item);
            });
        }
        ContentRelease saved = releaseRepository.save(previous);

        auditService.recordWithActor(actorId, "ADMIN", "RELEASE_ROLLED_BACK",
                "content_release", releaseId, Map.of("status", ContentRelease.STATUS_PUBLISHED),
                Map.of("restored", saved.getReleaseId()));
        log.warn("Rolled back release {} to {}", releaseId, saved.getReleaseId());
        return saved;
    }

    // ---------------------------------------------------------------------
    // Client manifest
    // ---------------------------------------------------------------------

    public Optional<ContentRelease> getLatestPublished() {
        return releaseRepository.findTopByStatusOrderByPublishedAtDesc(ContentRelease.STATUS_PUBLISHED);
    }

    /**
     * Manifest served to Unity. Returns an explicit empty manifest when nothing
     * has been published, so the client can show a safe "content unavailable"
     * state instead of failing to parse a response.
     */
    public Map<String, Object> getManifest() {
        Optional<ContentRelease> latest = getLatestPublished();

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("generatedAt", Instant.now());

        if (latest.isEmpty()) {
            manifest.put("releaseId", null);
            manifest.put("version", "0.0.0");
            manifest.put("manifestVersion", manifestVersion);
            manifest.put("targetClientVersions", List.of());
            manifest.put("bundles", List.of());
            return manifest;
        }

        ContentRelease release = latest.get();
        List<Map<String, Object>> bundles = new ArrayList<>();
        for (ContentRelease.ReleaseItemRef ref : release.getItems()) {
            versionRepository.findByItemSkuAndVersion(ref.getItemSku(), ref.getVersion())
                    .ifPresent(v -> bundles.add(Map.of(
                            "itemSku", v.getItemSku(),
                            "version", v.getVersion(),
                            "bundleKey", v.getBundleKey(),
                            "sha256", v.getSha256(),
                            "sizeBytes", v.getSizeBytes(),
                            "minimumClientVersion", v.getMinimumClientVersion()
                    )));
        }

        manifest.put("releaseId", release.getReleaseId());
        manifest.put("version", release.getVersion());
        manifest.put("manifestVersion", release.getManifestVersion());
        manifest.put("targetClientVersions", release.getTargetClientVersions());
        manifest.put("publishedAt", release.getPublishedAt());
        manifest.put("bundles", bundles);
        return manifest;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void requireAllVersionsValid(ContentRelease release) {
        List<String> invalid = new ArrayList<>();
        for (ContentRelease.ReleaseItemRef ref : release.getItems()) {
            ItemVersion entity = versionRepository
                    .findByItemSkuAndVersion(ref.getItemSku(), ref.getVersion())
                    .orElse(null);
            if (entity == null || !ItemVersion.VALIDATION_VALID.equals(entity.getValidationStatus())) {
                invalid.add(ref.getItemSku() + "@" + ref.getVersion());
            }
        }
        if (!invalid.isEmpty()) {
            throw new InvalidStateException("Release contains unvalidated content: " + String.join(", ", invalid));
        }
    }

    private ItemVersion resolveVersion(String itemSku, String version) {
        if (isBlank(itemSku)) {
            throw new InvalidStateException("items[].itemSku is required");
        }
        return (isBlank(version)
                ? latestValidVersion(itemSku)
                : versionRepository.findByItemSkuAndVersion(itemSku, version))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No valid item version for " + itemSku + (isBlank(version) ? "" : "@" + version)));
    }

    private static String string(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static List<String> stringList(Object value, List<String> fallback) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(String::valueOf).filter(s -> !s.isBlank()).toList();
        }
        return fallback;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Normalizes a checksum for comparison/storage in tests and tooling. */
    public static String normalizeSha256(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
