package com.railbound.portal.bundles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/coin-bundles")
@Tag(name = "Coin Bundles", description = "Real Coin bundle catalog and pricing")
public class CoinBundleController {

    private final CoinBundleService bundleService;

    public CoinBundleController(CoinBundleService bundleService) {
        this.bundleService = bundleService;
    }

    @GetMapping
    @Operation(summary = "List all active Real Coin bundles with pricing and bonus details")
    public ResponseEntity<List<CoinBundle>> getActiveBundles() {
        return ResponseEntity.ok(bundleService.getActiveBundles());
    }
}
