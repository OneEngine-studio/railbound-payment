package com.railbound.portal.shop;

import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/shop")
@Tag(name = "Shop", description = "Armory catalog and Real Coin purchases")
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping("/items")
    @Operation(summary = "List published catalog items with optional category filtering")
    public ResponseEntity<List<ShopItem>> getCatalogItems(
            @RequestParam(required = false) ItemCategory category) {
        return ResponseEntity.ok(shopService.getPublishedItems(category));
    }

    @GetMapping("/items/{sku}")
    @Operation(summary = "Get item detail and compatibility specification")
    public ResponseEntity<ShopItem> getItemDetail(@PathVariable String sku) {
        return ResponseEntity.ok(shopService.getItemBySku(sku));
    }

    @GetMapping("/categories")
    @Operation(summary = "List supported item categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(Arrays.stream(ItemCategory.values()).map(Enum::name).toList());
    }

    @PostMapping("/purchases")
    @Operation(summary = "Atomically purchase an item using Real Coins")
    public ResponseEntity<Map<String, Object>> purchaseItem(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, String> request) {
        String userId = SecurityUtils.getCurrentUserId();
        String itemSku = request.get("itemSku");

        Map<String, Object> result = shopService.purchaseItem(userId, itemSku, idempotencyKey);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/purchases")
    @Operation(summary = "Get player purchase history")
    public ResponseEntity<List<ShopPurchase>> getMyPurchases() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(shopService.getPurchaseHistory(userId));
    }
}
