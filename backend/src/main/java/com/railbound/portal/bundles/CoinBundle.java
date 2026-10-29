package com.railbound.portal.bundles;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "coin_bundles")
public class CoinBundle {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sku;

    private String title;
    private long baseCoins;
    private long bonusCoins;
    private long totalCoins;
    private long fiatAmountMinor; // e.g. 999 cents = $9.99
    private String currency; // "USD"
    private boolean active;
    private String badgeText;
    private int sortOrder;

    public CoinBundle() {
        this.active = true;
        this.currency = "USD";
    }

    public CoinBundle(String sku, String title, long baseCoins, long bonusCoins, long fiatAmountMinor, String badgeText, int sortOrder) {
        this.sku = sku;
        this.title = title;
        this.baseCoins = baseCoins;
        this.bonusCoins = bonusCoins;
        this.totalCoins = baseCoins + bonusCoins;
        this.fiatAmountMinor = fiatAmountMinor;
        this.currency = "USD";
        this.active = true;
        this.badgeText = badgeText;
        this.sortOrder = sortOrder;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getBaseCoins() {
        return baseCoins;
    }

    public void setBaseCoins(long baseCoins) {
        this.baseCoins = baseCoins;
        this.totalCoins = this.baseCoins + this.bonusCoins;
    }

    public long getBonusCoins() {
        return bonusCoins;
    }

    public void setBonusCoins(long bonusCoins) {
        this.bonusCoins = bonusCoins;
        this.totalCoins = this.baseCoins + this.bonusCoins;
    }

    public long getTotalCoins() {
        return totalCoins;
    }

    public void setTotalCoins(long totalCoins) {
        this.totalCoins = totalCoins;
    }

    public long getFiatAmountMinor() {
        return fiatAmountMinor;
    }

    public void setFiatAmountMinor(long fiatAmountMinor) {
        this.fiatAmountMinor = fiatAmountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getBadgeText() {
        return badgeText;
    }

    public void setBadgeText(String badgeText) {
        this.badgeText = badgeText;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
