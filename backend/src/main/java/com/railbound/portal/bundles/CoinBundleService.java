package com.railbound.portal.bundles;

import com.railbound.portal.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CoinBundleService {

    private final CoinBundleRepository bundleRepository;

    public CoinBundleService(CoinBundleRepository bundleRepository) {
        this.bundleRepository = bundleRepository;
    }

    public List<CoinBundle> getActiveBundles() {
        return bundleRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    public CoinBundle getBundleBySku(String sku) {
        return bundleRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Bundle not found for SKU: " + sku));
    }
}
