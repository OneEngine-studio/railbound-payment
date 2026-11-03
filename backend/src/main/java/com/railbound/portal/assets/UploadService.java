package com.railbound.portal.assets;

import com.railbound.portal.common.InvalidStateException;
import com.railbound.portal.common.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side upload grants with type/size allowlists.
 *
 * <p>The browser never chooses the object key, so an upload cannot overwrite
 * another item's media or escape the storage root. Content type and size are
 * pinned in the grant and re-checked when the bytes arrive.</p>
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM")
            .withZone(ZoneOffset.UTC);

    /** Content type -> file extension. Anything else is rejected. */
    private static final Map<String, String> ALLOWED_TYPES = new LinkedHashMap<>();
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    static {
        ALLOWED_TYPES.put("image/png", "png");
        ALLOWED_TYPES.put("image/jpeg", "jpg");
        ALLOWED_TYPES.put("image/webp", "webp");
        ALLOWED_TYPES.put("application/octet-stream", "bundle");
    }

    private final UploadTokenRepository tokenRepository;
    private final MediaStorage mediaStorage;
    private final long maxImageBytes;
    private final long maxBundleBytes;

    public UploadService(
            UploadTokenRepository tokenRepository,
            MediaStorage mediaStorage,
            @Value("${app.storage.max-image-bytes:5242880}") long maxImageBytes,
            @Value("${app.storage.max-bundle-bytes:52428800}") long maxBundleBytes) {
        this.tokenRepository = tokenRepository;
        this.mediaStorage = mediaStorage;
        this.maxImageBytes = maxImageBytes;
        this.maxBundleBytes = maxBundleBytes;
    }

    public Map<String, Object> createGrant(String actorId, String contentType, long declaredSize) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase();
        String extension = ALLOWED_TYPES.get(normalized);
        if (extension == null) {
            throw new InvalidStateException("Unsupported upload content type: " + contentType
                    + " (allowed: " + String.join(", ", ALLOWED_TYPES.keySet()) + ")");
        }

        long limit = IMAGE_TYPES.contains(normalized) ? maxImageBytes : maxBundleBytes;
        if (declaredSize > limit) {
            throw new InvalidStateException("File is too large: " + declaredSize + " bytes (limit " + limit + ")");
        }

        String objectKey = "uploads/" + MONTH.format(Instant.now()) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        tokenRepository.save(new UploadToken(token, objectKey, normalized, limit, actorId, expiresAt));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("uploadUrl", "/api/v1/uploads/" + token);
        response.put("method", "PUT");
        response.put("objectKey", objectKey);
        response.put("contentType", normalized);
        response.put("maxBytes", limit);
        response.put("expiresAt", expiresAt);
        return response;
    }

    public UploadResult acceptUpload(String token, byte[] data) {
        UploadToken grant = tokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Upload grant not found or already used"));

        if (!grant.isUsable(Instant.now())) {
            throw new InvalidStateException("Upload grant has expired or was already used");
        }
        if (data == null || data.length == 0) {
            throw new InvalidStateException("Upload body is empty");
        }
        if (data.length > grant.getMaxBytes()) {
            throw new InvalidStateException("Upload exceeds the granted size limit");
        }

        // The key is server-generated, but resolve it again defensively.
        mediaStorage.store(grant.getObjectKey(), data, grant.getContentType());

        grant.setUsedAt(Instant.now());
        tokenRepository.save(grant);

        String sha256 = sha256Hex(data);
        log.info("Stored upload {} ({} bytes, {})", grant.getObjectKey(), data.length, grant.getContentType());
        return new UploadResult(grant.getObjectKey(), grant.getContentType(), data.length, sha256,
                "/api/v1/media/" + grant.getObjectKey());
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record UploadResult(String objectKey, String contentType, long sizeBytes,
                               String sha256, String publicUrl) {
    }
}
