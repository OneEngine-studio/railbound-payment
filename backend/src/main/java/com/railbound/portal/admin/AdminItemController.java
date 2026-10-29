package com.railbound.portal.admin;

import com.railbound.portal.common.DuplicateResourceException;
import com.railbound.portal.common.ResourceNotFoundException;
import com.railbound.portal.shop.PublicationStatus;
import com.railbound.portal.shop.ShopItem;
import com.railbound.portal.shop.ShopItemRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/items")
@Tag(name = "Admin Items", description = "Catalog authoring, publishing and archiving")
public class AdminItemController {

    private final ShopItemRepository itemRepository;

    public AdminItemController(ShopItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @GetMapping
    @Operation(summary = "Admin list all catalog items including drafts")
    public ResponseEntity<List<ShopItem>> getAllItems() {
        return ResponseEntity.ok(itemRepository.findAll());
    }

    @GetMapping("/{sku}")
    @Operation(summary = "Admin inspect item draft by SKU")
    public ResponseEntity<ShopItem> getItem(@PathVariable String sku) {
        ShopItem item = itemRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + sku));
        return ResponseEntity.ok(item);
    }

    @PostMapping
    @Operation(summary = "Admin create draft item")
    public ResponseEntity<ShopItem> createItem(@RequestBody ShopItem item) {
        if (itemRepository.findBySku(item.getSku()).isPresent()) {
            throw new DuplicateResourceException("Item with SKU already exists: " + item.getSku());
        }
        item.setPublicationStatus(PublicationStatus.DRAFT);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        ShopItem saved = itemRepository.save(item);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/{sku}")
    @Operation(summary = "Admin update draft metadata")
    public ResponseEntity<ShopItem> updateItem(@PathVariable String sku, @RequestBody Map<String, Object> updates) {
        ShopItem item = itemRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + sku));

        if (updates.containsKey("title")) item.setTitle((String) updates.get("title"));
        if (updates.containsKey("description")) item.setDescription((String) updates.get("description"));
        if (updates.containsKey("realCoinPrice")) {
            item.setRealCoinPrice(((Number) updates.get("realCoinPrice")).longValue());
        }
        item.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(itemRepository.save(item));
    }

    @PostMapping("/{sku}/publish")
    @Operation(summary = "Admin publish approved item to live store")
    public ResponseEntity<ShopItem> publishItem(@PathVariable String sku) {
        ShopItem item = itemRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + sku));
        item.setPublicationStatus(PublicationStatus.PUBLISHED);
        item.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(itemRepository.save(item));
    }

    @PostMapping("/{sku}/archive")
    @Operation(summary = "Admin retire item from live store (preserves player ownership)")
    public ResponseEntity<ShopItem> archiveItem(@PathVariable String sku) {
        ShopItem item = itemRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + sku));
        item.setPublicationStatus(PublicationStatus.ARCHIVED);
        item.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(itemRepository.save(item));
    }
}
