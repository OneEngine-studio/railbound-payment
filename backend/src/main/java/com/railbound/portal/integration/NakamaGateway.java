package com.railbound.portal.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional bridge that pushes a notification to Nakama so online Unity clients
 * can update without polling.
 *
 * <p>Delivery is intentionally best-effort: the in-app notification is already
 * persisted by the time this runs, and {@code GET /game/sync} remains the
 * authoritative recovery path. When the webhook URL is blank the gateway is a
 * no-op, which keeps local development self-contained.</p>
 */
@Service
public class NakamaGateway {

    private static final Logger log = LoggerFactory.getLogger(NakamaGateway.class);

    private final String webhookUrl;
    private final String serviceKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public NakamaGateway(
            @Value("${app.nakama.notification-webhook-url:}") String webhookUrl,
            @Value("${app.internal.service-key}") String serviceKey,
            ObjectMapper objectMapper) {
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.serviceKey = serviceKey;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isConfigured() {
        return !webhookUrl.isEmpty();
    }

    /**
     * @throws Exception when Nakama is configured but unreachable, so the outbox
     *                   relay can retry with backoff instead of silently dropping
     *                   the live-update signal.
     */
    public void push(String nakamaUserId, String title, String body, Map<String, Object> payload) throws Exception {
        if (!isConfigured() || nakamaUserId == null || nakamaUserId.isBlank()) {
            return;
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("nakamaUserId", nakamaUserId);
        request.put("title", title);
        request.put("body", body);
        request.put("payload", payload == null ? Map.of() : payload);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Internal-Service-Key", serviceKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Nakama notification webhook returned HTTP " + response.statusCode());
        }
        log.debug("Pushed notification to Nakama for user {}", nakamaUserId);
    }
}
