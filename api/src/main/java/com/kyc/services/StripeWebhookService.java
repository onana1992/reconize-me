package com.kyc.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.config.KycProperties;
import com.kyc.web.ApiException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final CreditService credits;
    private final KycProperties properties;
    private final ObjectMapper objectMapper;

    public StripeWebhookService(CreditService credits, KycProperties properties, ObjectMapper objectMapper) {
        this.credits = credits;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Boolean> handle(String payload, String signature) {
        String secret = properties.stripe().webhookSecret();
        if (secret == null || secret.isBlank()) {
            throw ApiException.dependencyUnavailable("Stripe webhook secret is missing");
        }
        if (signature == null || signature.isBlank()) {
            throw ApiException.invalidSignature();
        }
        try {
            Webhook.constructEvent(payload, signature, secret);
        } catch (SignatureVerificationException ex) {
            throw ApiException.invalidSignature();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw ApiException.invalidSignature();
        }
        String eventId = text(root, "id");
        String type = text(root, "type");
        if (!"checkout.session.completed".equals(type)) {
            log.info("stripe webhook event_id={} type={} code=ignored", eventId, type);
            return Map.of("received", true);
        }
        JsonNode session = root.path("data").path("object");
        if (!"paid".equals(text(session, "payment_status"))) {
            log.info("stripe webhook event_id={} type={} code=unpaid", eventId, type);
            return Map.of("received", true);
        }
        JsonNode metadata = session.path("metadata");
        credits.topupFromCheckout(
                eventId,
                text(session, "id"),
                text(session, "customer"),
                parseUuid(text(metadata, "organization_id")),
                parseUuid(text(metadata, "user_id")),
                parseLong(text(metadata, "pack_minor")));
        log.info("stripe webhook event_id={} type={} code=processed", eventId, type);
        return Map.of("received", true);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
