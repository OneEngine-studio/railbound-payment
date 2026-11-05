package com.railbound.portal.identity;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Hs256JwtTest {

    private static final byte[] KEY = "defaultencryptionkey".getBytes(StandardCharsets.UTF_8); // < 256 bits, like Nakama's default

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sign(String payloadJson, byte[] key) throws Exception {
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64(payloadJson.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        String sig = b64(mac.doFinal((header + "." + payload).getBytes(StandardCharsets.US_ASCII)));
        return header + "." + payload + "." + sig;
    }

    @Test
    void acceptsValidTokenEvenWithShortKey() throws Exception {
        String token = sign("{\"uid\":\"u1\",\"usn\":\"Viper\"}", KEY);
        assertThat(Hs256Jwt.verifyAndGetPayload(token, KEY)).contains("\"uid\":\"u1\"");
    }

    @Test
    void rejectsTamperedPayloadWrongKeyAndGarbage() throws Exception {
        String token = sign("{\"uid\":\"u1\"}", KEY);
        String[] parts = token.split("\\.");
        String forgedPayload = b64("{\"uid\":\"admin\"}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> Hs256Jwt.verifyAndGetPayload(parts[0] + "." + forgedPayload + "." + parts[2], KEY))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> Hs256Jwt.verifyAndGetPayload(token, "another-key".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> Hs256Jwt.verifyAndGetPayload("garbage", KEY)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> Hs256Jwt.verifyAndGetPayload(parts[0] + "." + parts[1] + ".", KEY))
                .isInstanceOf(SecurityException.class);
    }
}
