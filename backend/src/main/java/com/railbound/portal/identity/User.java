package com.railbound.portal.identity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    @Indexed(unique = true)
    private String nakamaUserId;

    private String displayName;
    private String email;
    private String role; // PLAYER, CONTENT_MANAGER, ADMIN, FINANCE_SUPPORT, SUPER_ADMIN
    private String status; // ACTIVE, SUSPENDED, BANNED
    private Instant createdAt;
    private Instant updatedAt;

    public User() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.status = "ACTIVE";
        this.role = "PLAYER";
    }

    public User(String userId, String nakamaUserId, String displayName, String role) {
        this();
        this.userId = userId;
        this.nakamaUserId = nakamaUserId;
        this.displayName = displayName;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getNakamaUserId() {
        return nakamaUserId;
    }

    public void setNakamaUserId(String nakamaUserId) {
        this.nakamaUserId = nakamaUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
