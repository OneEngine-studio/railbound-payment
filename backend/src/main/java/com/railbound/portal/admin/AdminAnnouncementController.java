package com.railbound.portal.admin;

import com.railbound.portal.announcements.Announcement;
import com.railbound.portal.announcements.AnnouncementService;
import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/announcements")
@Tag(name = "Admin Announcements", description = "Admin news management and publication")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;
    private final com.railbound.portal.audit.AuditService auditService;

    public AdminAnnouncementController(AnnouncementService announcementService, com.railbound.portal.audit.AuditService auditService) {
        this.announcementService = announcementService;
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Admin list all announcements including drafts")
    public ResponseEntity<List<Announcement>> getAll() {
        return ResponseEntity.ok(announcementService.getAllForAdmin());
    }

    @PostMapping
    @Operation(summary = "Admin create announcement draft")
    public ResponseEntity<Announcement> create(@RequestBody Announcement draft) {
        String adminId = SecurityUtils.getCurrentUserId();
        draft.setCreatedBy(adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(announcementService.createAnnouncement(draft));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Admin publish announcement to game feed and web portal")
    public ResponseEntity<Announcement> publish(@PathVariable String id) {
        Announcement published = announcementService.publishAnnouncement(id);
        auditService.record(SecurityUtils.getCurrentUserId(), "ANNOUNCEMENT_PUBLISHED", id, "SUCCESS", null);
        return ResponseEntity.ok(published);
    }
}
