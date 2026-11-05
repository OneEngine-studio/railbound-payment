package com.railbound.portal.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Writes audit records. It joins the caller's MongoDB transaction when there is one, so an audit
 * entry exists if and only if the audited change was committed.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String actor, String action, String target, String status, String details) {
        String auditId = "aud_" + UUID.randomUUID().toString().replace("-", "");
        repository.save(new AuditLog(auditId, actor == null ? "UNKNOWN" : actor, action, target, status, details));
        log.info("AUDIT actor={} action={} target={} status={}", actor, action, target, status);
    }

    public List<AuditLog> recent() {
        return repository.findTop200ByOrderByTimestampDesc();
    }
}
