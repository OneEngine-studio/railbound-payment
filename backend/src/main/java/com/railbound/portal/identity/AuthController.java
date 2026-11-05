package com.railbound.portal.identity;

import com.railbound.portal.security.SecurityUser;
import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.railbound.portal.common.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Player session and identity operations")
public class AuthController {

    private final IdentityService identityService;
    private final NakamaTokenVerifier nakamaTokenVerifier;
    private final boolean mockTokenEnabled;

    public AuthController(IdentityService identityService,
                          NakamaTokenVerifier nakamaTokenVerifier,
                          @Value("${app.auth.mock-token-enabled:true}") boolean mockTokenEnabled) {
        this.identityService = identityService;
        this.nakamaTokenVerifier = nakamaTokenVerifier;
        this.mockTokenEnabled = mockTokenEnabled;
    }

    @PostMapping("/nakama-login")
    @Operation(summary = "Exchange a verified Nakama session token for a portal session")
    public ResponseEntity<Map<String, Object>> nakamaLogin(@RequestBody Map<String, String> request) {
        NakamaTokenVerifier.NakamaIdentity identity = nakamaTokenVerifier.verify(request.get("token"));
        // Role is never taken from the client: new accounts are always PLAYER, existing accounts
        // keep whatever role is stored in the database (admins are promoted there, by hand).
        User user = identityService.getOrCreateUser(identity.userId(), identity.username(), "PLAYER");
        String token = identityService.generateTokenForUser(user);
        return ResponseEntity.ok(sessionBody(token, user));
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

    @PostMapping("/logout")
    @Operation(summary = "Logout current session")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mock-token")
    @Operation(summary = "Generate dev/mock JWT token for testing or login demo")
    public ResponseEntity<Map<String, Object>> generateMockToken(@RequestBody Map<String, String> request) {
        if (!mockTokenEnabled) {
            throw new ResourceNotFoundException("Mock login is disabled on this server");
        }
        String nakamaUserId = request.getOrDefault("nakamaUserId", "00000000-0000-0000-0000-000000000001");
        String displayName = request.getOrDefault("displayName", "ViperActual");
        String role = request.getOrDefault("role", "PLAYER");

        User user = identityService.getOrCreateUser(nakamaUserId, displayName, role);
        String token = identityService.generateTokenForUser(user);

        return ResponseEntity.ok(sessionBody(token, user));
    }

    private static Map<String, Object> sessionBody(String token, User user) {
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
