package com.railbound.portal.flags;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Runtime feature flag with an optional effective window.
 *
 * <p>Flags are evaluated server-side; the frontend only receives the resolved
 * values from {@code GET /game/config}, so a client cannot enable a feature by
 * editing local storage.</p>
 */
@Document(collection = "feature_flags")
public class FeatureFlag {

    @Id
    private String id;

    @Indexed(unique = true)
    private String flag;

    private String description;
    private String audience; // ALL, BETA, ADMIN
    private boolean enabled;

    private Instant effectiveFrom;
    private Instant effectiveTo;

    private String updatedBy;
    private Instant updatedAt;

    public FeatureFlag() {
        this.audience = "ALL";
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    public FeatureFlag(String flag, String description, boolean enabled) {
        this();
        this.flag = flag;
        this.description = description;
        this.enabled = enabled;
    }

    /** A flag only applies while it is inside its configured window. */
    public boolean isActiveAt(Instant moment) {
        if (!enabled) {
            return false;
        }
        if (effectiveFrom != null && moment.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !moment.isAfter(effectiveTo);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(Instant effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(Instant effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
