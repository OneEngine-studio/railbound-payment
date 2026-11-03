package com.railbound.portal.releases;

import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Content release administration.
 *
 * <p>Approval is available to content managers; pushing a release live is
 * restricted to administrators so publishing requires two different people in
 * a real deployment.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/releases")
@Tag(name = "Admin Releases", description = "Approve and publish Unity content bundles")
public class AdminReleaseController {

    private final ReleaseService releaseService;

    public AdminReleaseController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping
    @Operation(summary = "List content releases")
    public ResponseEntity<List<ContentRelease>> list() {
        return ResponseEntity.ok(releaseService.listReleases());
    }

    @GetMapping("/{releaseId}")
    @Operation(summary = "Inspect one content release")
    public ResponseEntity<ContentRelease> get(@PathVariable String releaseId) {
        return ResponseEntity.ok(releaseService.getRelease(releaseId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Draft a release from validated item versions")
    public ResponseEntity<ContentRelease> create(@RequestBody Map<String, Object> request) {
        ContentRelease release = releaseService.createRelease(request, SecurityUtils.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(release);
    }

    @PostMapping("/{releaseId}/approve")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Approve a drafted release for publication")
    public ResponseEntity<ContentRelease> approve(@PathVariable String releaseId) {
        return ResponseEntity.ok(releaseService.approveRelease(releaseId, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/{releaseId}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Publish an approved release to game clients")
    public ResponseEntity<ContentRelease> publish(@PathVariable String releaseId) {
        return ResponseEntity.ok(releaseService.publishRelease(releaseId, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/{releaseId}/rollback")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Roll the live manifest back to the previous release")
    public ResponseEntity<ContentRelease> rollback(@PathVariable String releaseId) {
        return ResponseEntity.ok(releaseService.rollbackRelease(releaseId, SecurityUtils.getCurrentUserId()));
    }
}
