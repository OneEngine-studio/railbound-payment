package com.railbound.portal.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extractBearerToken(request);
        if (token != null) {
            Claims claims = tokenProvider.validateAndGetClaims(token);
            if (claims != null) {
                authenticate(claims, request);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7)
                : null;
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        String userId = claims.getSubject();
        String nakamaUserId = claims.get("nakamaUserId", String.class);
        String role = claims.get("role", String.class);
        String displayName = claims.get("displayName", String.class);

        var user = new SecurityUser(
                userId,
                nakamaUserId,
                role != null ? role : "PLAYER",
                displayName);

        var auth = new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
