package com.railbound.portal.identity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Minimal, dependency-free HS256 JWT signature verifier.
 *
 * <p>Nakama signs session tokens with HMAC-SHA256 using its {@code session.encryption_key}, which is
 * often shorter than the 256 bits the jjwt library insists on, so verification is done here with
 * plain JCA. This class only checks the signature and returns the raw payload JSON; expiry and
 * claim handling belong to the caller. The signature is ALWAYS verified as HS256 regardless of what
 * the token's header claims, so "alg":"none" style downgrade tokens are simply rejected.
 */
public final class Hs256Jwt {

    private Hs256Jwt() {}

    /**
     * @return the decoded payload JSON when the signature is valid
     * @throws SecurityException when the token is malformed or the signature does not match
     */
    public static String verifyAndGetPayload(String token, byte[] key) {
        if (token == null || key == null || key.length == 0) {
            throw new SecurityException("Invalid token");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[2].isEmpty()) {
            throw new SecurityException("Invalid token");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new SecurityException("Invalid token signature");
            }
            return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new SecurityException("Invalid token");
        }
    }
}
