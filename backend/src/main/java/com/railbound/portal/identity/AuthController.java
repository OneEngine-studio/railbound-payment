package com.railbound.portal.identity;

import com.railbound.portal.common.InvalidStateException;
import com.railbound.portal.security.SecurityUser;
import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Player session and identity operations")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final IdentityService identityService;
    private final NakamaTokenVerifier nakamaTokenVerifier;
    private final boolean mockLoginEnabled;

    public AuthController(IdentityService identityService,
                          NakamaTokenVerifier nakamaTokenVerifier,
                          @Value("${app.auth.mock-login-enabled:true}") boolean mockLoginEnabled) {
        this.identityService = identityService;
        this.nakamaTokenVerifier = nakamaTokenVerifier;
        this.mockLoginEnabled = mockLoginEnabled;
        if (mockLoginEnabled) {
            log.warn("Mock login is ENABLED. Set APP_AUTH_MOCK_LOGIN_ENABLED=false before exposing this portal publicly.");
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated player session")
    public ResponseEntity<Map<String, Object>> getCurrentSession() {
        SecurityUser current = SecurityUtils.getCurrentUser();
        if (current == null) {
            return ResponseEntity.status(401).build();
        }
        User user = identityService.getUserByUserId(current.getUserId());
        return ResponseEntity.ok(Map.of(
                "userId", user.getUserId(),
                "nakamaUserId", user.getNakamaUserId(),
                "displayName", user.getDisplayName(),
                "role", user.getRole(),
                "status", user.getStatus()
        ));
    }

    /**
     * Exchanges a Nakama session token (obtained from the game's Nakama client)
     * for a portal session token. The portal never sees a game password.
     */
    @PostMapping("/nakama")
    @Operation(summary = "Exchange a Nakama session token for a portal session")
    public ResponseEntity<Map<String, Object>> exchangeNakamaToken(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        NakamaTokenVerifier.NakamaIdentity identity = nakamaTokenVerifier.verify(token)
                .orElseThrow(() -> new InvalidStateException("Nakama session token is invalid or expired"));

        // Role is never accepted from the client: an existing user keeps their
        // stored role, a new user is always a PLAYER.
        User user = identityService.getOrCreateUser(
                identity.nakamaUserId(),
                identity.displayName() != null ? identity.displayName() : "Railbound Player",
                "PLAYER"
        );

        return ResponseEntity.ok(sessionResponse(user));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout current session")
    public ResponseEntity<Void> logout() {
        // Sessions are stateless bearer tokens; the client discards its token.
        // Short token lifetimes plus the rate limiter bound the replay window.
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mock-token")
    @Operation(summary = "Development-only demo login (disabled in production)")
    public ResponseEntity<Map<String, Object>> generateMockToken(@RequestBody Map<String, String> request) {
        if (!mockLoginEnabled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String nakamaUserId = request.getOrDefault("nakamaUserId", "00000000-0000-0000-0000-000000000001");
        String displayName = request.getOrDefault("displayName", "ViperActual");

        // Ignore any client-supplied role. The stored role for an existing user
        // is authoritative; new demo users are always PLAYER.
        User user = identityService.getOrCreateUser(nakamaUserId, displayName, "PLAYER");
        return ResponseEntity.ok(sessionResponse(user));
    }

    private Map<String, Object> sessionResponse(User user) {
        String token = identityService.generateTokenForUser(user);
        return Map.of(
                "token", token,
                "user", Map.of(
                        "userId", user.getUserId(),
                        "nakamaUserId", user.getNakamaUserId(),
                        "displayName", user.getDisplayName(),
                        "role", user.getRole()
                )
        );
    }
}
