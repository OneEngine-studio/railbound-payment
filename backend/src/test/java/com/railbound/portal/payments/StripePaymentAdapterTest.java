package com.railbound.portal.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class StripePaymentAdapterTest {

    private static final String SECRET = "whsec_test_only";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String sign(String body, long t, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((t + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void acceptsAFreshCorrectlySignedBody() throws Exception {
        Instant now = Instant.now();
        String body = "{\"id\":\"evt_1\"}";
        String header = "t=" + now.getEpochSecond() + ",v1=" + sign(body, now.getEpochSecond(), SECRET);
        assertTrue(StripePaymentAdapter.verifySignature(body, header, SECRET, now));
    }

    @Test
    void acceptsWhenAnyOfSeveralSignaturesMatches() throws Exception {
        Instant now = Instant.now();
        String body = "{}";
        String header = "t=" + now.getEpochSecond() + ",v1=" + "00".repeat(32) + ",v1=" + sign(body, now.getEpochSecond(), SECRET);
        assertTrue(StripePaymentAdapter.verifySignature(body, header, SECRET, now));
    }

    @Test
    void rejectsATamperedBody() throws Exception {
        Instant now = Instant.now();
        String header = "t=" + now.getEpochSecond() + ",v1=" + sign("{\"amount\":100}", now.getEpochSecond(), SECRET);
        assertFalse(StripePaymentAdapter.verifySignature("{\"amount\":1}", header, SECRET, now));
    }

    @Test
    void rejectsTheWrongSecret() throws Exception {
        Instant now = Instant.now();
        String header = "t=" + now.getEpochSecond() + ",v1=" + sign("{}", now.getEpochSecond(), "whsec_other");
        assertFalse(StripePaymentAdapter.verifySignature("{}", header, SECRET, now));
    }

    @Test
    void rejectsAReplayedOldSignature() throws Exception {
        Instant now = Instant.now();
        long old = now.getEpochSecond() - 3600;
        String header = "t=" + old + ",v1=" + sign("{}", old, SECRET);
        assertFalse(StripePaymentAdapter.verifySignature("{}", header, SECRET, now));
    }

    @Test
    void rejectsMissingOrMalformedHeadersAndEmptySecret() {
        Instant now = Instant.now();
        assertFalse(StripePaymentAdapter.verifySignature("{}", null, SECRET, now));
        assertFalse(StripePaymentAdapter.verifySignature("{}", "garbage", SECRET, now));
        assertFalse(StripePaymentAdapter.verifySignature("{}", "t=abc,v1=zz", SECRET, now));
        assertFalse(StripePaymentAdapter.verifySignature("{}", "t=" + now.getEpochSecond() + ",v1=" + "00".repeat(32), "", now));
    }

    private StripePaymentAdapter adapter() {
        return new StripePaymentAdapter("sk_test_x", SECRET, "https://api.stripe.com", MAPPER);
    }

    @Test
    void parsesAPaidCheckoutSession() throws Exception {
        var payload = MAPPER.readTree("""
                {"id":"evt_9","type":"checkout.session.completed","data":{"object":{
                  "id":"cs_test_1","payment_status":"paid","amount_total":999,"currency":"usd",
                  "client_reference_id":"ord_1","metadata":{"orderId":"ord_1"}}}}""");
        var event = adapter().parseWebhook(payload).orElseThrow();
        assertEquals("ord_1", event.orderId());
        assertEquals("evt_9", event.eventId());
        assertEquals("cs_test_1", event.providerSessionId());
        assertEquals(999L, event.amountMinor());
        assertEquals("usd", event.currency());
    }

    @Test
    void ignoresUnpaidSessionsAndOtherEvents() throws Exception {
        var unpaid = MAPPER.readTree("""
                {"id":"evt_1","type":"checkout.session.completed","data":{"object":{
                  "id":"cs_1","payment_status":"unpaid","metadata":{"orderId":"ord_1"}}}}""");
        assertTrue(adapter().parseWebhook(unpaid).isEmpty());
        var other = MAPPER.readTree("{\"id\":\"evt_2\",\"type\":\"charge.refunded\",\"data\":{\"object\":{}}}");
        assertTrue(adapter().parseWebhook(other).isEmpty());
    }

    @Test
    void isNotConfiguredWithoutBothKeys() {
        assertFalse(new StripePaymentAdapter("", SECRET, "https://api.stripe.com", MAPPER).isConfigured());
        assertFalse(new StripePaymentAdapter("sk_test_x", "", "https://api.stripe.com", MAPPER).isConfigured());
        assertTrue(adapter().isConfigured());
    }
}
