package com.kyc.controllers;

import com.kyc.services.StripeWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Stripe")
public class StripeWebhookController {

    private final StripeWebhookService webhooks;

    public StripeWebhookController(StripeWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping
    @Operation(
            summary = "Recevoir un événement Stripe Checkout",
            description = "Appelé par Stripe. Le corps est le JSON brut de l'événement. "
                    + "L'authentification est le header Stripe-Signature (secret whsec_). "
                    + "Seul checkout.session.completed avec payment_status=paid crédite le solde.")
    public Map<String, Boolean> handle(
            HttpServletRequest request, @RequestHeader(value = "Stripe-Signature", required = false) String signature)
            throws IOException {
        String payload = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        return webhooks.handle(payload, signature);
    }
}
