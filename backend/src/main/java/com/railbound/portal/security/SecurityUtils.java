package com.railbound.portal.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static SecurityUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SecurityUser user) {
            return user;
        }
        return null;
    }

    public static String getCurrentUserId() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getUserId() : null;
    }

    public static String getCurrentNakamaUserId() {
        SecurityUser user = getCurrentUser();
        return user != null ? user.getNakamaUserId() : null;
    }
}
