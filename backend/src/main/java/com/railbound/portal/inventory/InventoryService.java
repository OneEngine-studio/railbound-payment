package com.railbound.portal.inventory;

import com.railbound.portal.common.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryEntitlementRepository entitlementRepository;

    public InventoryService(InventoryEntitlementRepository entitlementRepository) {
        this.entitlementRepository = entitlementRepository;
    }

    public List<InventoryEntitlement> getMyEntitlements(String userId) {
        return entitlementRepository.findByUserIdAndState(userId, "ACTIVE");
    }

    public Optional<InventoryEntitlement> getEntitlement(String userId, String sku) {
        return entitlementRepository.findByUserIdAndItemSku(userId, sku);
    }

    public boolean isOwned(String userId, String sku) {
        return entitlementRepository.existsByUserIdAndItemSkuAndState(userId, sku, "ACTIVE");
    }

    public InventoryEntitlement equipItem(String userId, String itemSku, String slot, String weaponSku) {
        InventoryEntitlement target = entitlementRepository.findByUserIdAndItemSku(userId, itemSku)
                .orElseThrow(() -> new ResourceNotFoundException("Entitlement not owned for SKU: " + itemSku));

        // Unequip any current item in this slot for this user
        entitlementRepository.findByUserIdAndEquippedSlot(userId, slot)
                .ifPresent(current -> {
                    current.setEquippedSlot(null);
                    current.setEquippedWeaponSku(null);
                    entitlementRepository.save(current);
                });

        target.setEquippedSlot(slot);
        target.setEquippedWeaponSku(weaponSku);
        log.info("Player {} equipped {} to slot {}", userId, itemSku, slot);
        return entitlementRepository.save(target);
    }

    public InventoryEntitlement unequipSlot(String userId, String slot) {
        InventoryEntitlement item = entitlementRepository.findByUserIdAndEquippedSlot(userId, slot)
                .orElseThrow(() -> new ResourceNotFoundException("No item currently equipped in slot: " + slot));

        item.setEquippedSlot(null);
        item.setEquippedWeaponSku(null);
        log.info("Player {} unequipped slot {}", userId, slot);
        return entitlementRepository.save(item);
    }
}
