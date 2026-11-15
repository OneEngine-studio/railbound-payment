package com.railbound.portal.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railbound.portal.common.InvalidStateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Signs a launcher player in by asking the OneEngine platform API who the bearer token belongs to. No secret is shared
 * between the two services, and the role is never taken from the caller: everyone who arrives this way is a PLAYER.
 */
@Service
public class OneEngineAuthService {

    private final String apiUrl;
    private final ObjectMapper mapper;
    private final IdentityService identityService;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public OneEngineAuthService(
            @Value("${app.oneengine.api-url}") String apiUrl,
            ObjectMapper mapper,
            IdentityService identityService) {
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.mapper = mapper;
        this.identityService = identityService;
    }

    public record Session(String token, User user) {}

    public Session exchange(String bearerToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + "/v1/me"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + bearerToken)
                .GET()
                .build();
        JsonNode me;
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new AccessDeniedException("OneEngine sign-in is not valid");
            }
            if (response.statusCode() / 100 != 2) {
                throw new InvalidStateException("The OneEngine service could not confirm your sign-in");
            }
            me = mapper.readTree(response.body());
        } catch (IOException e) {
            throw new InvalidStateException("The OneEngine service could not be reached");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidStateException("The OneEngine service could not be reached");
        }

        String platformUserId = me.path("userId").asText("");
        if (platformUserId.isBlank()) {
            throw new AccessDeniedException("OneEngine sign-in is not valid");
        }
        if (!me.path("emailVerified").asBoolean(false)) {
            throw new AccessDeniedException("Confirm your email before buying coins");
        }
        String displayName = me.path("displayName").asText("Player");
        User user = identityService.getOrCreateUser("oe:" + platformUserId, displayName, "PLAYER");
        return new Session(identityService.generateTokenForUser(user), user);
    }
}
