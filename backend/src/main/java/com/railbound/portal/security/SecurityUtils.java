package com.railbound.portal.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static SecurityUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof SecurityUser user
            ? user
            : null;
    }

    public static String getCurrentUserId() {
        return Optional.ofNullable(getCurrentUser())
            .map(SecurityUser::getUserId)
            .orElse(null);
    }

    public static String getCurrentNakamaUserId() {
        return Optional.ofNullable(getCurrentUser())
            .map(SecurityUser::getNakamaUserId)
            .orElse(null);
    }
}
