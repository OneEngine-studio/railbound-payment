package com.railbound.portal.flags;

import com.railbound.portal.audit.AuditService;
import com.railbound.portal.common.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves public runtime configuration.
 *
 * <p>Missing flags fall back to {@code instructions.md} defaults, so a fresh
 * database still advertises Real Coins and keeps Game Coins disabled. Values are
 * cached-free on purpose: an admin toggle takes effect on the next request.</p>
 */
@Service
public class FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);

    private final FeatureFlagRepository repository;
    private final AuditService auditService;
    private final String minimumClientVersion;

    public FeatureFlagService(
            FeatureFlagRepository repository,
            AuditService auditService,
            @Value("${app.game.minimum-client-version:1.0.0}") String minimumClientVersion) {
        this.repository = repository;
        this.auditService = auditService;
        this.minimumClientVersion = minimumClientVersion;
    }

    public boolean isEnabled(String flag, boolean defaultValue) {
        return repository.findByFlag(flag)
                .map(f -> f.isActiveAt(Instant.now()))
                .orElse(defaultValue);
    }

    /** Safe, public configuration consumed by web and Unity clients. */
    public Map<String, Object> getPublicConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("realCoinsEnabled", isEnabled("realCoinsEnabled", true));
        config.put("gameCoinsEnabled", isEnabled("gameCoinsEnabled", false));
        config.put("shopEnabled", isEnabled("shopEnabled", true));
        config.put("topUpEnabled", isEnabled("topUpEnabled", true));
        config.put("notificationsEnabled", isEnabled("notificationsEnabled", true));
        config.put("minClientVersion", minimumClientVersion);
        config.put("evaluatedAt", Instant.now());
        return config;
    }

    public FeatureFlag setEnabled(String flag, boolean enabled, String actorId) {
        FeatureFlag flagDoc = repository.findByFlag(flag)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown feature flag: " + flag));

        Map<String, Object> before = Map.of("enabled", flagDoc.isEnabled());
        flagDoc.setEnabled(enabled);
        flagDoc.setUpdatedBy(actorId);
        flagDoc.setUpdatedAt(Instant.now());
        FeatureFlag saved = repository.save(flagDoc);

        auditService.recordWithActor(actorId, "ADMIN", "FEATURE_FLAG_UPDATED", "feature_flag", flag,
                before, Map.of("enabled", saved.isEnabled()));
        log.info("Feature flag {} set to {} by {}", flag, enabled, actorId);
        return saved;
    }

    public java.util.List<FeatureFlag> getAll() {
        return repository.findAllByOrderByFlagAsc();
    }
}
