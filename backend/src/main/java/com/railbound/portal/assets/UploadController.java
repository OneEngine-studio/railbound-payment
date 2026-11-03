package com.railbound.portal.assets;

import com.railbound.portal.common.InvalidStateException;
import com.railbound.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Staged media uploads and public media delivery.
 *
 * <p>{@code presign} is admin-only. The tokenised {@code PUT} is intentionally
 * unauthenticated because it is called by the browser directly and the grant
 * itself is the capability: single use, 15 minutes, pinned type and size.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Media", description = "Staged asset uploads and public media delivery")
public class UploadController {

    private final UploadService uploadService;
    private final MediaStorage mediaStorage;

    public UploadController(UploadService uploadService, MediaStorage mediaStorage) {
        this.uploadService = uploadService;
        this.mediaStorage = mediaStorage;
    }

    @PostMapping("/admin/uploads/presign")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Create a single-use upload grant for preview media or a content bundle")
    public ResponseEntity<Map<String, Object>> presign(@RequestBody Map<String, Object> request) {
        String contentType = request.get("contentType") == null
                ? null
                : String.valueOf(request.get("contentType"));
        long size = request.get("sizeBytes") instanceof Number number ? number.longValue() : 0L;
        return ResponseEntity.ok(uploadService.createGrant(SecurityUtils.getCurrentUserId(), contentType, size));
    }

    @PutMapping("/uploads/{token}")
    @Operation(summary = "Upload bytes using a previously issued grant")
    public ResponseEntity<UploadService.UploadResult> upload(@PathVariable String token,
                                                             @RequestBody byte[] data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(uploadService.acceptUpload(token, data));
    }

    @GetMapping("/media/**")
    @Operation(summary = "Serve a stored preview image or content bundle")
    public ResponseEntity<byte[]> getMedia(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int marker = uri.indexOf("/media/");
        if (marker < 0) {
            throw new InvalidStateException("Invalid media path");
        }
        String key = uri.substring(marker + "/media/".length());

        Optional<byte[]> data = mediaStorage.read(key);
        if (data.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .contentType(mediaTypeFor(key))
                .body(data.get());
    }

    private MediaType mediaTypeFor(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
