package com.kyc.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class CryptoTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private CryptoTokens() {}

    public static String randomHostedToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String randomApiKey() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "ky_test_" + HexFormat.of().formatHex(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String sha256HexPeppered(String pepper, String value) {
        return sha256Hex(pepper + ":" + value);
    }
}
