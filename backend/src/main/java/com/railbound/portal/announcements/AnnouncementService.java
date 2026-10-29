package com.railbound.portal.announcements;

import com.railbound.portal.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    public AnnouncementService(AnnouncementRepository announcementRepository) {
        this.announcementRepository = announcementRepository;
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
        return announcementRepository.save(draft);
    }

    public Announcement publishAnnouncement(String id) {
        Announcement announcement = getAnnouncementById(id);
        announcement.setStatus("PUBLISHED");
        announcement.setPublishAt(Instant.now());
        return announcementRepository.save(announcement);
    }
}
