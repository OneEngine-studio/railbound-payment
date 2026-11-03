package com.railbound.portal.outbox;

import com.railbound.portal.identity.User;
import com.railbound.portal.identity.UserRepository;
import com.railbound.portal.integration.NakamaGateway;
import com.railbound.portal.notifications.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Projection step for outbox events: turns a domain event into a player
 * notification and (when configured) a Nakama live-update push.
 *
 * <p>Notification ids are derived from the outbox {@code eventId}, so a relay
 * retry cannot duplicate the player's inbox entry.</p>
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final NakamaGateway nakamaGateway;

    public OutboxDispatcher(NotificationService notificationService,
                            UserRepository userRepository,
                            NakamaGateway nakamaGateway) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.nakamaGateway = nakamaGateway;
    }

    public void dispatch(OutboxEvent event) throws Exception {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        String userId = asString(payload.get("userId"));

        // Broadcast-style events (releases/announcements) have no single user and
        // are surfaced through the news feed plus /game/sync instead.
        if (userId == null || userId.isBlank()) {
            log.debug("Outbox event {} has no target user; treated as broadcast", event.getEventId());
            return;
        }

        String title = titleFor(event.getType());
        String body = bodyFor(event.getType(), payload);
        if (title == null) {
            log.warn("No notification template for outbox event type {}", event.getType());
            return;
        }

        notificationService.create(
                "ntf_" + event.getEventId(),
                userId,
                event.getType(),
                title,
                body,
                payload
        );

        User user = userRepository.findByUserId(userId).orElse(null);
        if (user != null) {
            nakamaGateway.push(user.getNakamaUserId(), title, body, payload);
        }
    }

    private String titleFor(String type) {
        return switch (type) {
            case OutboxService.WALLET_CREDITED -> "Real Coins credited";
            case OutboxService.PURCHASE_COMPLETED -> "Armory purchase complete";
            case OutboxService.REFUND_COMPLETED -> "Refund processed";
            case OutboxService.RELEASE_PUBLISHED -> "Content update available";
            case OutboxService.ANNOUNCEMENT_PUBLISHED -> "New sector briefing";
            default -> null;
        };
    }

    private String bodyFor(String type, Map<String, Object> payload) {
        return switch (type) {
            case OutboxService.WALLET_CREDITED -> {
                Object coins = payload.getOrDefault("coins", 0);
                Object orderId = payload.getOrDefault("orderId", "");
                yield coins + " Real Coins were added to your wallet (order " + orderId + ").";
            }
            case OutboxService.PURCHASE_COMPLETED -> {
                Object item = payload.getOrDefault("itemSku", "item");
                Object spent = payload.getOrDefault("coinsSpent", 0);
                Object balance = payload.getOrDefault("balance", 0);
                yield item + " was added to your inventory for " + spent
                        + " Real Coins. Balance: " + balance + ".";
            }
            case OutboxService.REFUND_COMPLETED -> {
                Object orderId = payload.getOrDefault("orderId", "");
                yield "Your refund for order " + orderId + " has been completed.";
            }
            case OutboxService.RELEASE_PUBLISHED -> {
                Object version = payload.getOrDefault("version", "1.0.0");
                yield "Content release " + version + " is now live for compatible clients.";
            }
            case OutboxService.ANNOUNCEMENT_PUBLISHED -> {
                Object title = payload.getOrDefault("title", "");
                yield String.valueOf(title);
            }
            default -> "";
        };
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
