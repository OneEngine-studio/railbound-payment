package com.railbound.portal.announcements;

import com.railbound.portal.audit.AuditService;
import com.railbound.portal.common.ResourceNotFoundException;
import com.railbound.portal.outbox.OutboxService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final OutboxService outboxService;
    private final AuditService auditService;

    public AnnouncementService(AnnouncementRepository announcementRepository,
                               OutboxService outboxService,
                               AuditService auditService) {
        this.announcementRepository = announcementRepository;
        this.outboxService = outboxService;
        this.auditService = auditService;
    }

    public List<Announcement> getPublishedNews() {
        return announcementRepository.findByStatusOrderByPublishAtDesc("PUBLISHED");
    }

    public Announcement getAnnouncementById(String id) {
        return announcementRepository.findByAnnouncementId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found: " + id));
    }

    public List<Announcement> getAllForAdmin() {
        return announcementRepository.findAllByOrderByPublishAtDesc();
    }

    public Announcement createAnnouncement(Announcement draft) {
        if (draft.getAnnouncementId() == null || draft.getAnnouncementId().isBlank()) {
            draft.setAnnouncementId("ann_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        draft.setPublishAt(Instant.now());
        Announcement saved = announcementRepository.save(draft);
        auditService.record("ANNOUNCEMENT_CREATED", "announcement", saved.getAnnouncementId(),
                Map.of(), Map.of("title", String.valueOf(saved.getTitle()),
                        "status", String.valueOf(saved.getStatus())));
        return saved;
    }

    public Announcement publishAnnouncement(String id) {
        Announcement announcement = getAnnouncementById(id);
        String previous = announcement.getStatus();
        announcement.setStatus("PUBLISHED");
        announcement.setPublishAt(Instant.now());
        Announcement saved = announcementRepository.save(announcement);

        // Broadcast event: no target user, surfaced through the news feed.
        outboxService.enqueue(OutboxService.ANNOUNCEMENT_PUBLISHED, "announcement", id,
                Map.of("announcementId", id, "title", String.valueOf(saved.getTitle())));

        auditService.record("ANNOUNCEMENT_PUBLISHED", "announcement", id,
                Map.of("status", String.valueOf(previous)),
                Map.of("status", saved.getStatus()));
        return saved;
    }
}
