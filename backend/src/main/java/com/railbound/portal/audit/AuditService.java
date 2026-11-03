package com.railbound.portal.audit;

import com.railbound.portal.security.SecurityUser;
import com.railbound.portal.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Writes and reads the administrative audit trail.
 *
 * <p>Any key whose name looks like a credential is redacted before the record is
 * persisted, so an accidental {@code Map} dump cannot leak a merchant secret,
 * token or password into the database or the admin UI.</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final Set<String> SENSITIVE_KEY_FRAGMENTS = Set.of(
            "secret", "password", "token", "apikey", "api_key", "authorization",
            "card", "cvv", "pan", "md5sig", "hash"
    );

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Records an administrative action. The current actor is taken from the
     * security context rather than from request JSON so it cannot be spoofed.
     */
    public AuditLog record(String action, String targetType, String targetId,
                           Map<String, Object> before, Map<String, Object> after) {
        SecurityUser current = SecurityUtils.getCurrentUser();
        String actorId = current != null ? current.getUserId() : "SYSTEM";
        String actorRole = current != null ? current.getRole() : "SYSTEM";

        AuditLog entry = new AuditLog(
                actorId,
                actorRole,
                action,
                targetType,
                targetId,
                sanitize(before),
                sanitize(after),
                null
        );

        AuditLog saved = repository.save(entry);
        log.info("AUDIT {} {} {} by {}", action, targetType, targetId, actorId);
        return saved;
    }

    public AuditLog recordWithActor(String actorId, String actorRole, String action,
                                    String targetType, String targetId,
                                    Map<String, Object> before, Map<String, Object> after) {
        AuditLog saved = repository.save(new AuditLog(
                actorId != null ? actorId : "SYSTEM",
                actorRole != null ? actorRole : "SYSTEM",
                action,
                targetType,
                targetId,
                sanitize(before),
                sanitize(after),
                null
        ));
        log.info("AUDIT {} {} {} by {}", action, targetType, targetId, actorId);
        return saved;
    }

    public Page<AuditLog> getAuditLogs(int page, int size) {
        int boundedSize = Math.min(Math.max(size, 1), 200);
        Pageable pageable = PageRequest.of(Math.max(page, 0), boundedSize);
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private Map<String, Object> sanitize(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            String normalized = key.toLowerCase(Locale.ROOT);
            boolean sensitive = SENSITIVE_KEY_FRAGMENTS.stream().anyMatch(normalized::contains);
            safe.put(key, sensitive ? "[REDACTED]" : value);
        });
        return safe;
    }
}
