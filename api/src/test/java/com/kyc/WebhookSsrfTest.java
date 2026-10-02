package com.kyc;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kyc.services.WebhookSsrfGuard;
import org.junit.jupiter.api.Test;

class WebhookSsrfTest {

    @Test
    void liveDeniesPrivateAndHttp() {
        assertThrows(IllegalArgumentException.class, () -> WebhookSsrfGuard.validate("http://example.com/h", true));
        assertThrows(IllegalArgumentException.class, () -> WebhookSsrfGuard.validate("https://127.0.0.1/h", true));
        assertThrows(IllegalArgumentException.class, () -> WebhookSsrfGuard.validate("https://169.254.169.254/", true));
        WebhookSsrfGuard.validate("https://example.com/hooks", true);
    }

    @Test
    void testAllowsLocalhostHttp() {
        WebhookSsrfGuard.validate("http://localhost:8080/hook", false);
        WebhookSsrfGuard.validate("https://example.com/hook", false);
    }
}
