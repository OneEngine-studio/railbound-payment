package com.railbound.portal.audit;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Append-only record of a sensitive action (payment fulfillment, refunds, catalog publication...).
 * Rows are only ever inserted by {@link AuditService}; no API exposes update or delete.
 * Never put secrets, card data or full personal data in {@code details}.
 */
@Document(collection = "audit_logs")
public class AuditLog {

    @Id
    private String id;

    private String auditId;
    private String actor;
    private String action;
    private String target;
    private String status;
    private String details;

    @Indexed
    private Instant timestamp;

    public AuditLog() {
        this.timestamp = Instant.now();
    }

    public AuditLog(String auditId, String actor, String action, String target, String status, String details) {
        this.auditId = auditId;
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.status = status;
        this.details = details;
        this.timestamp = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getAuditId() {
        return auditId;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getStatus() {
        return status;
    }

    public String getDetails() {
        return details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
