package com.railbound.portal.shop;

import com.railbound.portal.common.DuplicateResourceException;
import com.railbound.portal.common.InvalidStateException;
import com.railbound.portal.common.ResourceNotFoundException;
import com.railbound.portal.inventory.InventoryEntitlement;
import com.railbound.portal.inventory.InventoryEntitlementRepository;
import com.railbound.portal.wallet.Wallet;
import com.railbound.portal.wallet.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ShopService {

    private static final Logger log = LoggerFactory.getLogger(ShopService.class);

    private final ShopItemRepository itemRepository;
    private final ShopPurchaseRepository purchaseRepository;
    private final InventoryEntitlementRepository entitlementRepository;
    private final WalletService walletService;

    public ShopService(
            ShopItemRepository itemRepository,
            ShopPurchaseRepository purchaseRepository,
            InventoryEntitlementRepository entitlementRepository,
            WalletService walletService) {
        this.itemRepository = itemRepository;
        this.purchaseRepository = purchaseRepository;
        this.entitlementRepository = entitlementRepository;
        this.walletService = walletService;
    }

    public List<ShopItem> getPublishedItems(ItemCategory category) {
        if (category != null) {
            return itemRepository.findByPublicationStatusAndCategory(PublicationStatus.PUBLISHED, category);
        }
        return itemRepository.findByPublicationStatus(PublicationStatus.PUBLISHED);
    }

    public ShopItem getItemBySku(String sku) {
        return itemRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found for SKU: " + sku));
    }

    public List<ShopPurchase> getPurchaseHistory(String userId) {
        return purchaseRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Executes atomic purchase of an item using Real Coins.
     * Enforces:
     * - Item is PUBLISHED
     * - Item is not already owned (non-stackable permanent item rule)
     * - Atomic wallet debit (realCoins >= price)
     * - Single entitlement and ledger creation within MongoDB transaction
     */
    @Transactional
    public Map<String, Object> purchaseItem(String userId, String itemSku, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<ShopPurchase> existing = purchaseRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent purchase request detected for user {} and key {}", userId, idempotencyKey);
                ShopPurchase p = existing.get();
                Wallet wallet = walletService.getWalletByUserId(userId);
                return Map.of(
                        "purchaseId", p.getPurchaseId(),
                        "itemSku", p.getItemSku(),
                        "coinsSpent", p.getPriceSnapshot(),
                        "newBalance", wallet.getRealCoins(),
                        "ownership", "ACTIVE"
                );
            }
        }

        ShopItem item = getItemBySku(itemSku);
        if (item.getPublicationStatus() != PublicationStatus.PUBLISHED) {
            throw new InvalidStateException("Item is not available for purchase: " + item.getPublicationStatus());
        }

        if (entitlementRepository.existsByUserIdAndItemSkuAndState(userId, itemSku, "ACTIVE")) {
            throw new DuplicateResourceException("Player already owns this permanent item: " + itemSku);
        }

        String purchaseId = "pur_" + UUID.randomUUID().toString().replace("-", "");
        String entitlementId = "ent_" + UUID.randomUUID().toString().replace("-", "");

        // 1. Debit coins atomically
        String desc = "Armory Purchase: " + item.getTitle() + " (" + item.getSku() + ")";
        Wallet wallet = walletService.debitRealCoins(userId, item.getRealCoinPrice(), "SHOP_PURCHASE", purchaseId, desc);

        // 2. Grant inventory entitlement
        InventoryEntitlement entitlement = new InventoryEntitlement(entitlementId, userId, itemSku, purchaseId);
        entitlementRepository.save(entitlement);

        // 3. Record purchase audit
        ShopPurchase purchase = new ShopPurchase(purchaseId, userId, itemSku, item.getRealCoinPrice(), idempotencyKey);
        purchaseRepository.save(purchase);

        log.info("Successfully executed purchase {} of item {} for user {}. Spent: {}, New Balance: {}",
                purchaseId, itemSku, userId, item.getRealCoinPrice(), wallet.getRealCoins());

        return Map.of(
                "purchaseId", purchaseId,
                "itemSku", itemSku,
                "coinsSpent", item.getRealCoinPrice(),
                "newBalance", wallet.getRealCoins(),
                "ownership", "ACTIVE"
        );
    }
}
