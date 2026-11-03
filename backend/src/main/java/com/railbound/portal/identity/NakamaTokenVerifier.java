package com.railbound.portal.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Verifies a Nakama session token with the shared Nakama server key.
 *
 * <p>Nakama issues HS256 session tokens signed with the server key; the portal
 * verifies the signature and expiry and then maps the stable {@code usid} claim
 * to a local user. This removes the need for a second password database.</p>
 *
 * <p>HMAC is computed directly instead of delegating to a JWT library because
 * RFC 7518 libraries reject keys shorter than 256 bits, while Nakama (and its
 * documented {@code defaultkey} default) accepts any key length. Verification
 * still checks the {@code alg} header, the signature and the expiry, and uses a
 * constant-time comparison.</p>
 */
@Component
public class NakamaTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(NakamaTokenVerifier.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Decoder BASE64_URL = Base64.getUrlDecoder();

    private final SecretKeySpec key;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public NakamaTokenVerifier(
            @Value("${app.nakama.server-key:defaultkey}") String serverKey,
            @Value("${app.nakama.token-verification-enabled:true}") boolean enabled,
            ObjectMapper objectMapper) {
        this.key = new SecretKeySpec(serverKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        if (serverKey.length() < 32) {
            log.warn("Nakama server key is shorter than 32 characters; use a long random key in production.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<NakamaIdentity> verify(String token) {
        if (!enabled || token == null || token.isBlank()) {
            return Optional.empty();
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            log.warn("Rejected malformed Nakama token (expected 3 segments)");
            return Optional.empty();
        }

        try {
            JsonNode header = objectMapper.readTree(BASE64_URL.decode(parts[0]));
            if (!"HS256".equals(header.path("alg").asText())) {
                log.warn("Rejected Nakama token with unsupported alg: {}", header.path("alg").asText());
                return Optional.empty();
            }

            byte[] expected = hmac(parts[0] + "." + parts[1]);
            byte[] actual = BASE64_URL.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                log.warn("Rejected Nakama token with an invalid signature");
                return Optional.empty();
            }

            JsonNode claims = objectMapper.readTree(BASE64_URL.decode(parts[1]));
            long expiresAt = claims.path("exp").asLong(0);
            if (expiresAt > 0 && Instant.ofEpochSecond(expiresAt).isBefore(Instant.now())) {
                log.warn("Rejected expired Nakama token");
                return Optional.empty();
            }

            String nakamaUserId = firstNonBlank(
                    text(claims, "usid"),
                    text(claims, "uid"),
                    text(claims, "sub")
            );
            if (nakamaUserId == null) {
                log.warn("Nakama token had no usid/uid/sub claim");
                return Optional.empty();
            }

            String username = firstNonBlank(text(claims, "username"), text(claims, "display_name"));
            return Optional.of(new NakamaIdentity(nakamaUserId, username));
        } catch (Exception e) {
            log.warn("Nakama token verification failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private byte[] hmac(String signingInput) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(key);
        return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public record NakamaIdentity(String nakamaUserId, String displayName) {
    }
}
