package com.railbound.portal.flags;

import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/feature-flags")
@Tag(name = "Admin Feature Flags", description = "Runtime toggles for portal and game features")
public class AdminFeatureFlagController {

    private final FeatureFlagService featureFlagService;

    public AdminFeatureFlagController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Operation(summary = "List every feature flag")
    public ResponseEntity<List<FeatureFlag>> list() {
        return ResponseEntity.ok(featureFlagService.getAll());
    }

    @PatchMapping("/{flag}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Enable or disable a feature flag")
    public ResponseEntity<FeatureFlag> update(@PathVariable String flag,
                                             @RequestBody Map<String, Object> body) {
        Object value = body.get("enabled");
        if (!(value instanceof Boolean enabled)) {
            throw new IllegalArgumentException("Body must contain a boolean 'enabled' field");
        }
        return ResponseEntity.ok(featureFlagService.setEnabled(flag, enabled, SecurityUtils.getCurrentUserId()));
    }
}
