package com.kyc.adapters;

import com.kyc.ports.ObjectStoragePort.SignedUrl;
import com.kyc.services.CryptoTokens;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

final class ObjectStorageHmac {

    private final String publicApiBase;
    private final String pepper;

    ObjectStorageHmac(String publicApiBase, String pepper) {
        String base = publicApiBase == null || publicApiBase.isBlank() ? "http://localhost:8080" : publicApiBase.trim();
        this.publicApiBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.pepper = pepper;
    }

    SignedUrl sign(String method, String objectKey, Duration ttl) {
        requireValidKey(objectKey);
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

    boolean verify(String method, String objectKey, long expiresEpoch, String signature) {
        if (signature == null || Instant.now().getEpochSecond() > expiresEpoch) {
            return false;
        }
        return expected(method, objectKey, expiresEpoch).equals(signature);
    }

    static void requireValidKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..") || objectKey.startsWith("/")) {
            throw new IllegalArgumentException("Invalid object key");
        }
    }

    private String expected(String method, String objectKey, long expiresEpoch) {
        return CryptoTokens.sha256HexPeppered(pepper, method + ":" + objectKey + ":" + expiresEpoch);
    }
}
