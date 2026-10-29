package com.railbound.portal.inventory;

import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory", description = "Player owned entitlements and equipment management")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/me")
    @Operation(summary = "List all active entitlements owned by current player")
    public ResponseEntity<List<InventoryEntitlement>> getMyInventory() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(inventoryService.getMyEntitlements(userId));
    }

    @GetMapping("/me/{sku}")
    @Operation(summary = "Get ownership status of a specific SKU")
    public ResponseEntity<Map<String, Object>> getOwnership(@PathVariable String sku) {
        String userId = SecurityUtils.getCurrentUserId();
        boolean owned = inventoryService.isOwned(userId, sku);
        return ResponseEntity.ok(Map.of("sku", sku, "owned", owned));
    }

    @PostMapping("/me/equip")
    @Operation(summary = "Server-validated equip action for an owned item")
    public ResponseEntity<InventoryEntitlement> equipItem(@RequestBody Map<String, String> request) {
        String userId = SecurityUtils.getCurrentUserId();
        String itemSku = request.get("itemSku");
        String slot = request.get("slot");
        String weaponSku = request.get("weaponSku");

        InventoryEntitlement entitlement = inventoryService.equipItem(userId, itemSku, slot, weaponSku);
        return ResponseEntity.ok(entitlement);
    }

    @PostMapping("/me/unequip")
    @Operation(summary = "Unequip item from a specific slot")
    public ResponseEntity<InventoryEntitlement> unequipItem(@RequestBody Map<String, String> request) {
        String userId = SecurityUtils.getCurrentUserId();
        String slot = request.get("slot");

        InventoryEntitlement entitlement = inventoryService.unequipSlot(userId, slot);
        return ResponseEntity.ok(entitlement);
    }
}
