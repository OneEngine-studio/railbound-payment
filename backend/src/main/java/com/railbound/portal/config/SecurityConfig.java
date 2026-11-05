package com.railbound.portal.config;

import com.railbound.portal.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
    private List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public catalog, bundles, news, and swagger docs
                        .requestMatchers(HttpMethod.GET, "/api/v1/coin-bundles/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/shop/items/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/shop/categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/news/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/game/catalog").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/game/content-manifest").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/game/releases/latest").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/game/config").permitAll()
                        .requestMatchers("/api/v1/auth/mock-token", "/api/v1/auth/nakama-login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/providers").permitAll()
                        // Authorized by a one-time checkout token inside the request body.
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/orders/*/hosted-form").permitAll()
                        // Provider callbacks: trust comes from the provider signature, checked in the adapter.
                        .requestMatchers("/api/v1/payments/webhooks/**").permitAll()
                        .requestMatchers("/api/v1/internal/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/actuator/**").permitAll()
                        
                        // Admin restricted operations
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "CONTENT_MANAGER", "FINANCE_SUPPORT", "SUPER_ADMIN")
                        
                        // All player authenticated endpoints
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Idempotency-Key", "X-Correlation-Id"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
