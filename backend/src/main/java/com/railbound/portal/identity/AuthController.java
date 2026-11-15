package com.railbound.portal.identity;

import com.railbound.portal.config.DevEndpoints;
import com.railbound.portal.security.SecurityUser;
import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Player session and identity operations")
public class AuthController {

    private final IdentityService identityService;
    private final DevEndpoints devEndpoints;
    private final OneEngineAuthService oneEngineAuth;

    public AuthController(IdentityService identityService, DevEndpoints devEndpoints, OneEngineAuthService oneEngineAuth) {
        this.identityService = identityService;
        this.devEndpoints = devEndpoints;
        this.oneEngineAuth = oneEngineAuth;
    }

    @PostMapping("/oneengine")
    @Operation(summary = "Exchange a OneEngine launcher sign-in (Authorization: Bearer) for a payments session")
    public ResponseEntity<Map<String, Object>> oneEngineSignIn(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() < 20 || authorization.length() > 4096) {
            return ResponseEntity.status(401).build();
        }
        OneEngineAuthService.Session session = oneEngineAuth.exchange(authorization.substring(7));
        return ResponseEntity.ok(Map.of(
                "token", session.token(),
                "user", Map.of(
                        "userId", session.user().getUserId(),
                        "displayName", session.user().getDisplayName()
                )
        ));
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
        devEndpoints.require();
        String nakamaUserId = request.getOrDefault("nakamaUserId", "00000000-0000-0000-0000-000000000001");
        String displayName = request.getOrDefault("displayName", "ViperActual");
        String role = request.getOrDefault("role", "PLAYER");

        User user = identityService.getOrCreateUser(nakamaUserId, displayName, role);
        String token = identityService.generateTokenForUser(user);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", Map.of(
                        "userId", user.getUserId(),
                        "nakamaUserId", user.getNakamaUserId(),
                        "displayName", user.getDisplayName(),
                        "role", user.getRole()
                )
        ));
    }
}
