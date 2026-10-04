package com.kyc.adapters;

import com.kyc.ports.ObjectStoragePort;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

public class S3ObjectStorage implements ObjectStoragePort, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

    private final S3Client s3;
    private final String bucket;
    private final String keyPrefix;
    private final ObjectStorageHmac hmac;

    public S3ObjectStorage(S3Client s3, String bucket, String keyPrefix, String publicApiBase, String pepper) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("S3 bucket is required");
        }
        this.s3 = s3;
        this.bucket = bucket.trim();
        this.keyPrefix = normalizePrefix(keyPrefix);
        this.hmac = new ObjectStorageHmac(publicApiBase, pepper);
        log.info("Object storage using S3 bucket={} prefix={}", this.bucket, this.keyPrefix);
    }

    @Override
    public SignedUrl createSignedUploadUrl(String objectKey, String contentType, Duration ttl) {
        return hmac.sign("PUT", objectKey, ttl);
    }

    @Override
    public SignedUrl createSignedGetUrl(String objectKey, Duration ttl) {
        return hmac.sign("GET", objectKey, ttl);
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            s3.headObject(head(objectKey));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw wrap("Unable to head object", e);
        } catch (SdkException e) {
            throw wrap("Unable to head object", e);
        }
    }

    @Override
    public long size(String objectKey) {
        try {
            HeadObjectResponse head = s3.headObject(head(objectKey));
            Long length = head.contentLength();
            return length == null ? 0 : length;
        } catch (NoSuchKeyException e) {
            return 0;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return 0;
            }
            throw wrap("Unable to size object", e);
        } catch (SdkException e) {
            throw wrap("Unable to size object", e);
        }
    }

    @Override
    public byte[] read(String objectKey) {
        try {
            ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key(objectKey))
                    .build());
            return bytes.asByteArray();
        } catch (SdkException e) {
            throw wrap("Unable to read object", e);
        }
    }

    @Override
    public void write(String objectKey, byte[] body, String contentType) {
        String type = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        try {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(s3Key(objectKey))
                            .contentType(type)
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .build(),
                    RequestBody.fromBytes(body == null ? new byte[0] : body));
        } catch (SdkException e) {
            throw wrap("Unable to write object", e);
        }
    }

    @Override
    public boolean verifySignature(String method, String objectKey, long expiresEpoch, String signature) {
        return hmac.verify(method, objectKey, expiresEpoch, signature);
    }

    @Override
    public void close() {
        s3.close();
    }

    private HeadObjectRequest head(String objectKey) {
        return HeadObjectRequest.builder().bucket(bucket).key(s3Key(objectKey)).build();
    }

    private String s3Key(String objectKey) {
        ObjectStorageHmac.requireValidKey(objectKey);
        return keyPrefix + objectKey;
    }

    static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String value = prefix.trim();
        if (value.contains("..")) {
            throw new IllegalArgumentException("Invalid S3 key prefix");
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.isEmpty()) {
            return "";
        }
        return value.endsWith("/") ? value : value + "/";
    }

    private static IllegalStateException wrap(String message, SdkException e) {
        return new IllegalStateException(message, e);
    }
}
