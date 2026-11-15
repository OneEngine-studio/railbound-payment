package com.railbound.portal.config;

import com.railbound.portal.common.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The mock token, mock checkout completion, mock payment provider and demo accounts exist only for local development.
 * They are off unless APP_DEV_ENDPOINTS_ENABLED=true, and answer "not found" when off.
 */
@Component
public class DevEndpoints {

    private final boolean enabled;

    public DevEndpoints(@Value("${app.dev-endpoints.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void require() {
        if (!enabled) {
            throw new ResourceNotFoundException("Not found");
        }
    }
}
