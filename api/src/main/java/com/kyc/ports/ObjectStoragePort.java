package com.kyc.ports;

import java.time.Duration;
import java.time.Instant;

/**
 * Port for object storage interactions (e.g., S3, filesystem).
 */
public interface ObjectStoragePort {

    /**
     * Represents a signed URL for accessing or uploading an object.
     *
     * @param url        The signed URL.
     * @param expiresAt  The expiration instant of the signed URL.
     */
    record SignedUrl(String url, Instant expiresAt) {}

    /**
     * Creates a signed URL for uploading an object, valid for the given TTL.
     *
     * @param objectKey     The key of the object to upload.
     * @param contentType   The MIME type of the object.
     * @param ttl           The duration for which the signed URL is valid.
     * @return              A signed upload URL.
     */
    SignedUrl createSignedUploadUrl(String objectKey, String contentType, Duration ttl);

    /**
     * Creates a signed URL for downloading an object, valid for the given TTL.
     *
     * @param objectKey     The key of the object to download.
     * @param ttl           The duration for which the signed URL is valid.
     * @return              A signed get/download URL.
     */
    SignedUrl createSignedGetUrl(String objectKey, Duration ttl);

    /**
     * Checks if an object exists at the specified key.
     *
     * @param objectKey The key to check.
     * @return          True if the object exists, otherwise false.
     */
    boolean exists(String objectKey);

    /**
     * Returns the size in bytes of the object at the specified key.
     *
     * @param objectKey The key to check.
     * @return          Size in bytes.
     */
    long size(String objectKey);

    /**
     * Reads the object at the specified key into a byte array.
     *
     * @param objectKey The key of the object.
     * @return          The object's data as a byte array.
     */
    byte[] read(String objectKey);

    /**
     * Writes the given byte array to the specified key in storage.
     *
     * @param objectKey     The key where the object should be stored.
     * @param body          Object data as a byte array.
     * @param contentType   MIME type of the object.
     */
    void write(String objectKey, byte[] body, String contentType);

    /**
     * Deletes the object at the specified key from storage.
     *
     * @param objectKey The key of the object to delete.
     */
    void delete(String objectKey);

    /**
     * Verifies a signature for a given method, object key, and expiration epoch.
     *
     * @param method        HTTP method (e.g., "GET", "PUT").
     * @param objectKey     The key of the object to access.
     * @param expiresEpoch  Expiration as a unix epoch in seconds.
     * @param signature     The signature to validate.
     * @return              True if the signature is valid, otherwise false.
     */
    boolean verifySignature(String method, String objectKey, long expiresEpoch, String signature);
}
