package com.railbound.portal.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railbound.portal.common.InvalidSignatureException;
import com.railbound.portal.common.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Verifies a Nakama session token (the one the Unity client holds after logging in to Nakama) so a
 * player can obtain a portal session without ever sending a password or choosing their own role.
 *
 * <p>Configure {@code app.nakama.session-encryption-key} with the same value as Nakama's
 * {@code session.encryption_key}. Nakama session tokens carry the user id in the {@code uid} claim
 * and the username in {@code usn}; confirm these claim names against your Nakama version.
 */
@Component
public class NakamaTokenVerifier {

    public record NakamaIdentity(String userId, String username) {}

    private final byte[] key;
    private final ObjectMapper objectMapper;

    public NakamaTokenVerifier(@Value("${app.nakama.session-encryption-key:}") String encryptionKey,
                               ObjectMapper objectMapper) {
        this.key = encryptionKey == null || encryptionKey.isBlank()
                ? new byte[0] : encryptionKey.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return key.length > 0;
    }

    public NakamaIdentity verify(String token) {
        if (!isConfigured()) {
            throw new ResourceNotFoundException("Nakama login is not configured on this server");
        }
        try {
            JsonNode claims = objectMapper.readTree(Hs256Jwt.verifyAndGetPayload(token, key));
            long exp = claims.path("exp").asLong(0);
            if (exp <= Instant.now().getEpochSecond()) {
                throw new InvalidSignatureException("Nakama session has expired");
            }
            String uid = claims.path("uid").asText("");
            if (uid.isBlank()) {
                throw new InvalidSignatureException("Nakama session has no user id");
            }
            String username = claims.path("usn").asText("");
            return new NakamaIdentity(uid, username.isBlank() ? "Player" : username);
        } catch (SecurityException e) {
            throw new InvalidSignatureException("Invalid Nakama session token");
        } catch (java.io.IOException e) {
            throw new InvalidSignatureException("Invalid Nakama session token");
        }
    }
}
