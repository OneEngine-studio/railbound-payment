package com.railbound.portal.wallet;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "wallets")
public class Wallet {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private long realCoins;
    private long gameCoins; // Reserved for soft progression

    @Version
    private Long version;

    private Instant updatedAt;

    public Wallet() {
        this.realCoins = 0;
        this.gameCoins = 0;
        this.updatedAt = Instant.now();
    }

    public Wallet(String userId, long initialRealCoins) {
        this.userId = userId;
        this.realCoins = initialRealCoins;
        this.gameCoins = 0;
        this.updatedAt = Instant.now();
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

    public long getRealCoins() {
        return realCoins;
    }

    public void setRealCoins(long realCoins) {
        this.realCoins = realCoins;
    }

    public long getGameCoins() {
        return gameCoins;
    }

    public void setGameCoins(long gameCoins) {
        this.gameCoins = gameCoins;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
