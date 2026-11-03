package com.railbound.portal.audit;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Append-only administrative audit record.
 *
 * <p>Only sanitized summaries are stored: never payment secrets, card data or
 * raw provider payloads. {@code before} / {@code after} are small maps of the
 * fields that changed so a reviewer can reconstruct an administrative decision
 * without reading application logs.</p>
 */
@Document(collection = "audit_logs")
public class AuditLog {

    @Id
    private String id;

    @Indexed
    private String actorId;

    private String actorRole;

    @Indexed
    private String action;

    private String targetType;
    private String targetId;

    private Map<String, Object> before;
    private Map<String, Object> after;

    /** Request correlation id so an audit row can be tied back to an API call. */
    private String correlationId;

    @Indexed
    private Instant createdAt;

    public AuditLog() {
        this.createdAt = Instant.now();
    }

    public AuditLog(String actorId, String actorRole, String action, String targetType,
                    String targetId, Map<String, Object> before, Map<String, Object> after,
                    String correlationId) {
        this();
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.before = before;
        this.after = after;
        this.correlationId = correlationId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public Map<String, Object> getBefore() {
        return before;
    }

    public void setBefore(Map<String, Object> before) {
        this.before = before;
    }

    public Map<String, Object> getAfter() {
        return after;
    }

    public void setAfter(Map<String, Object> after) {
        this.after = after;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
