package com.railbound.portal.announcements;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/news")
@Tag(name = "News", description = "Game announcements and patch releases")
public class NewsController {

    private final AnnouncementService announcementService;

    public NewsController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    @Operation(summary = "List published game news & announcements")
    public ResponseEntity<List<Announcement>> getNews() {
        return ResponseEntity.ok(announcementService.getPublishedNews());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get announcement detail")
    public ResponseEntity<Announcement> getAnnouncement(@PathVariable String id) {
        return ResponseEntity.ok(announcementService.getAnnouncementById(id));
    }
}
