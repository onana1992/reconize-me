package com.kyc.services;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WebhookSigner {

    private WebhookSigner() {}

    public static String sign(String secret, long unixSeconds, String rawBody) {
        String payload = unixSeconds + "." + rawBody;
        return "t=" + unixSeconds + ",v1=" + hmacHex(secret, payload);
    }

    public static String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }
}
