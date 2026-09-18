package com.kyc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class StripeWebhookSupport {

    static final String SECRET = "whsec_test";

    private StripeWebhookSupport() {}

    static String signedHeader(String payload) {
        try {
            long timestamp = Instant.now().getEpochSecond();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return "t=" + timestamp + ",v1=" + hex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String checkoutCompleted(
            String eventId, String sessionId, String customerId, String organizationId, String userId, long packMinor) {
        return """
                {"id":"%s","object":"event","api_version":"2024-06-20","created":1710000000,"type":"checkout.session.completed","livemode":false,"pending_webhooks":1,"data":{"object":{"id":"%s","object":"checkout.session","customer":"%s","payment_status":"paid","status":"complete","mode":"payment","currency":"usd","amount_total":%d,"metadata":{"organization_id":"%s","pack_minor":"%d","user_id":"%s"}}}}
                """
                .formatted(eventId, sessionId, customerId, packMinor, organizationId, packMinor, userId)
                .replace("\n", "");
    }
}
