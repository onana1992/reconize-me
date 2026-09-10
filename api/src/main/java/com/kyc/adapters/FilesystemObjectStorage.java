package com.kyc.adapters;

import com.kyc.config.KycProperties;
import com.kyc.ports.ObjectStoragePort;
import com.kyc.services.CryptoTokens;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;

public class FilesystemObjectStorage implements ObjectStoragePort {

    private final Path root;
    private final String publicApiBase;
    private final String pepper;

    public FilesystemObjectStorage(
            KycProperties properties, @Value("${kyc.public-api-base-url:http://localhost:8080}") String publicApiBase) {
        this.root = Path.of(properties.objectStorageRoot()).toAbsolutePath().normalize();
        this.publicApiBase = publicApiBase.endsWith("/")
                ? publicApiBase.substring(0, publicApiBase.length() - 1)
                : publicApiBase;
        this.pepper = properties.ipHashPepper();
    }

    @Override
    public SignedUrl createSignedUploadUrl(String objectKey, String contentType, Duration ttl) {
        return sign("PUT", objectKey, ttl);
    }

    @Override
    public SignedUrl createSignedGetUrl(String objectKey, Duration ttl) {
        return sign("GET", objectKey, ttl);
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
        if (Instant.now().getEpochSecond() > expiresEpoch) {
            return false;
        }
        return expected(method, objectKey, expiresEpoch).equals(signature);
    }

    private SignedUrl sign(String method, String objectKey, Duration ttl) {
        Instant expires = Instant.now().plus(ttl);
        long epoch = expires.getEpochSecond();
        String sig = expected(method, objectKey, epoch);
        String url = publicApiBase
                + "/v1/objects?key="
                + URLEncoder.encode(objectKey, StandardCharsets.UTF_8)
                + "&exp="
                + epoch
                + "&sig="
                + sig
                + "&method="
                + method;
        return new SignedUrl(url, expires);
    }

    private String expected(String method, String objectKey, long expiresEpoch) {
        return CryptoTokens.sha256HexPeppered(pepper, method + ":" + objectKey + ":" + expiresEpoch);
    }

    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..")) {
            throw new IllegalArgumentException("Invalid object key");
        }
        Path path = root.resolve(objectKey).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid object key");
        }
        return path;
    }
}
