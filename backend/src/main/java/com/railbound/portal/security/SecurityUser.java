package com.railbound.portal.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class SecurityUser implements UserDetails {

    private final String userId;
    private final String nakamaUserId;
    private final String role;
    private final String displayName;

    public SecurityUser(String userId, String nakamaUserId, String role, String displayName) {
        this.userId = userId;
        this.nakamaUserId = nakamaUserId;
        this.role = role;
        this.displayName = displayName;
    }

    public String getUserId() {
        return userId;
    }

    public String getNakamaUserId() {
        return nakamaUserId;
    }

    public String getRole() {
        return role;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return userId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
