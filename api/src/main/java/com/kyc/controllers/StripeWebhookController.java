package com.kyc.controllers;

import com.kyc.services.StripeWebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/webhooks/stripe")
@Hidden
public class StripeWebhookController {

    private final StripeWebhookService webhooks;

    public StripeWebhookController(StripeWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping
    public Map<String, Boolean> handle(
            HttpServletRequest request, @RequestHeader(value = "Stripe-Signature", required = false) String signature)
            throws IOException {
        String payload = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        return webhooks.handle(payload, signature);
    }
}
