package com.kyc.ports;

import java.time.Duration;
import java.time.Instant;

public interface ObjectStoragePort {

    record SignedUrl(String url, Instant expiresAt) {}

    SignedUrl createSignedUploadUrl(String objectKey, String contentType, Duration ttl);

    SignedUrl createSignedGetUrl(String objectKey, Duration ttl);

    boolean exists(String objectKey);

    long size(String objectKey);

    byte[] read(String objectKey);

    void write(String objectKey, byte[] body, String contentType);

    boolean verifySignature(String method, String objectKey, long expiresEpoch, String signature);
}
