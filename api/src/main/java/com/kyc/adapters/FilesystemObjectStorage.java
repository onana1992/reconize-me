package com.kyc.adapters;

import com.kyc.config.KycProperties;
import com.kyc.ports.ObjectStoragePort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;

public class FilesystemObjectStorage implements ObjectStoragePort {

    private final Path root;
    private final ObjectStorageHmac hmac;

    public FilesystemObjectStorage(
            KycProperties properties, @Value("${kyc.public-api-base-url:http://localhost:8080}") String publicApiBase) {
        this.root = Path.of(properties.objectStorageRoot()).toAbsolutePath().normalize();
        this.hmac = new ObjectStorageHmac(publicApiBase, properties.ipHashPepper());
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
        return Files.isRegularFile(resolve(objectKey));
    }

    @Override
    public long size(String objectKey) {
        try {
            return Files.size(resolve(objectKey));
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public byte[] read(String objectKey) {
        try {
            return Files.readAllBytes(resolve(objectKey));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read object", e);
        }
    }

    @Override
    public void write(String objectKey, byte[] body, String contentType) {
        Path path = resolve(objectKey);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, body);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write object", e);
        }
    }

    @Override
    public boolean verifySignature(String method, String objectKey, long expiresEpoch, String signature) {
        return hmac.verify(method, objectKey, expiresEpoch, signature);
    }

    private Path resolve(String objectKey) {
        ObjectStorageHmac.requireValidKey(objectKey);
        Path path = root.resolve(objectKey).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid object key");
        }
        return path;
    }
}
