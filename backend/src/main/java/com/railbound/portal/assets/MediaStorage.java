package com.railbound.portal.assets;

import java.util.Optional;

/**
 * Minimal object-storage abstraction.
 *
 * <p>Local disk is the default implementation so the portal runs without cloud
 * credentials. Swap in an S3/MinIO implementation without touching callers.</p>
 */
public interface MediaStorage {

    /** Stores bytes under a server-generated key and returns that key. */
    String store(String key, byte[] data, String contentType);

    Optional<byte[]> read(String key);

    boolean exists(String key);
}
