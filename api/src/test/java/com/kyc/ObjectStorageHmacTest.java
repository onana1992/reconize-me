package com.kyc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kyc.adapters.FilesystemObjectStorage;
import com.kyc.config.KycProperties;
import com.kyc.ports.ObjectStoragePort;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObjectStorageHmacTest {

    @TempDir
    Path tempDir;

    @Test
    void filesystemSignedPutRoundTrips() {
        FilesystemObjectStorage storage = new FilesystemObjectStorage(properties(), "http://api.example:8080/");
        ObjectStoragePort.SignedUrl signed =
                storage.createSignedUploadUrl("org/a/verifications/b/selfie/1", "image/jpeg", Duration.ofMinutes(5));
        assertTrue(signed.url().startsWith("http://api.example:8080/v1/objects?key="));
        assertTrue(signed.url().contains("method=PUT"));
        String query = signed.url().substring(signed.url().indexOf('?') + 1);
        String key = value(query, "key");
        long exp = Long.parseLong(value(query, "exp"));
        String sig = value(query, "sig");
        assertTrue(storage.verifySignature("PUT", key, exp, sig));
        assertFalse(storage.verifySignature("GET", key, exp, sig));
        assertFalse(storage.verifySignature("PUT", key, exp - 10, sig));
    }

    @Test
    void filesystemDeleteRemovesBytes() {
        FilesystemObjectStorage storage = new FilesystemObjectStorage(properties(), "http://localhost:8080");
        String key = "org/a/verifications/b/document/1";
        storage.write(key, new byte[] {1, 2, 3}, "image/jpeg");
        assertTrue(storage.exists(key));
        storage.delete(key);
        assertFalse(storage.exists(key));
        storage.delete(key);
    }

    @Test
    void rejectsBlankKey() {
        FilesystemObjectStorage storage = new FilesystemObjectStorage(properties(), "http://localhost:8080");
        assertThrows(IllegalArgumentException.class, () -> storage.exists(""));
        assertThrows(IllegalArgumentException.class, () -> storage.exists("../x"));
    }

    private KycProperties properties() {
        return new KycProperties(
                "http://localhost:3000",
                "http://localhost:3001",
                3600,
                "consent-v1",
                24,
                tempDir.toString(),
                "pepper",
                false,
                5,
                15,
                new KycProperties.Mail("log", "noreply@localhost", ""),
                new KycProperties.Billing("usd", 900L),
                new KycProperties.Stripe("log", "", "whsec_test"),
                new KycProperties.Aws(
                        false,
                        "ca-central-1",
                        new KycProperties.Aws.S3(false, "", "", "", "", ""),
                        new KycProperties.Aws.Textract("", "", ""),
                        new KycProperties.Aws.Rekognition("", "", "")));
    }

    private static String value(String query, String name) {
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name)) {
                return java.net.URLDecoder.decode(part.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException(name);
    }
}
