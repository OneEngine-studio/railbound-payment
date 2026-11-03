package com.railbound.portal.notifications;

import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Player notification inbox. Every query is scoped to the authenticated user;
 * the user id is never accepted from the request body.
 */
@RestController
@RequestMapping("/api/v1/players/me/notifications")
@Tag(name = "Notifications", description = "Player notification inbox")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List my latest notifications")
    public ResponseEntity<Map<String, Object>> getMyNotifications() {
        String userId = SecurityUtils.getCurrentUserId();
        List<Notification> notifications = notificationService.getMyNotifications(userId);
        return ResponseEntity.ok(Map.of(
                "notifications", notifications,
                "unreadCount", notificationService.getUnreadCount(userId)
        ));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one of my notifications as read")
    public ResponseEntity<Notification> markRead(@PathVariable String id) {
        return ResponseEntity.ok(notificationService.markRead(SecurityUtils.getCurrentUserId(), id));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark every notification as read")
    public ResponseEntity<Map<String, Object>> markAllRead() {
        int updated = notificationService.markAllRead(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(Map.of("updated", updated));
    }
}
