package com.kyc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.config.KycProperties;
import com.kyc.dto.webhook.UpsertWebhookRequest;
import com.kyc.dto.webhook.WebhookDeliveryListResponse;
import com.kyc.dto.webhook.WebhookEndpointResponse;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.Integration;
import com.kyc.entities.Verification;
import com.kyc.entities.WebhookDelivery;
import com.kyc.entities.WebhookEndpoint;
import com.kyc.enums.Enums;
import com.kyc.enums.WebhookDeliveryStatus;
import com.kyc.enums.WebhookEventType;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.WebhookDeliveryRepository;
import com.kyc.repositories.WebhookEndpointRepository;
import com.kyc.security.ApiPrincipal;
import com.kyc.security.ConsoleAuth;
import com.kyc.security.ConsolePrincipal;
import com.kyc.security.Permission;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration[] RETRY_DELAYS = {
        Duration.ZERO, Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofHours(1)
    };

    private final WebhookEndpointRepository endpoints;
    private final WebhookDeliveryRepository deliveries;
    private final IntegrationRepository integrations;
    private final AuditEventRepository auditEvents;
    private final KycProperties properties;
    private final ObjectMapper objectMapper;

    public WebhookService(
            WebhookEndpointRepository endpoints,
            WebhookDeliveryRepository deliveries,
            IntegrationRepository integrations,
            AuditEventRepository auditEvents,
            KycProperties properties,
            ObjectMapper objectMapper) {
        this.endpoints = endpoints;
        this.deliveries = deliveries;
        this.integrations = integrations;
        this.auditEvents = auditEvents;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WebhookEndpointResponse upsertBearer(ApiPrincipal principal, UpsertWebhookRequest request) {
        Integration integration = requireIntegration(principal.organizationId(), principal.integrationId());
        return upsert(principal.organizationId(), "api_key", principal.apiKeyId(), integration, request.url());
    }

    @Transactional
    public WebhookEndpointResponse upsertConsole(ConsolePrincipal principal, UUID integrationId, UpsertWebhookRequest request) {
        ConsoleAuth.require(principal, Permission.API_KEY_WRITE);
        Integration integration = requireIntegration(principal.organizationId(), integrationId);
        return upsert(principal.organizationId(), "user", principal.userId(), integration, request.url());
    }

    @Transactional(readOnly = true)
    public WebhookEndpointResponse getBearer(ApiPrincipal principal) {
        return get(principal.organizationId(), principal.integrationId());
    }

    @Transactional(readOnly = true)
    public WebhookEndpointResponse getConsole(ConsolePrincipal principal, UUID integrationId) {
        ConsoleAuth.require(principal, Permission.API_KEY_READ);
        return get(principal.organizationId(), integrationId);
    }

    @Transactional
    public void deleteBearer(ApiPrincipal principal) {
        delete(principal.organizationId(), "api_key", principal.apiKeyId(), principal.integrationId());
    }

    @Transactional
    public void deleteConsole(ConsolePrincipal principal, UUID integrationId) {
        ConsoleAuth.require(principal, Permission.API_KEY_WRITE);
        delete(principal.organizationId(), "user", principal.userId(), integrationId);
    }

    @Transactional
    public WebhookEndpointResponse rotateBearer(ApiPrincipal principal) {
        return rotate(principal.organizationId(), "api_key", principal.apiKeyId(), principal.integrationId());
    }

    @Transactional
    public WebhookEndpointResponse rotateConsole(ConsolePrincipal principal, UUID integrationId) {
        ConsoleAuth.require(principal, Permission.API_KEY_WRITE);
        return rotate(principal.organizationId(), "user", principal.userId(), integrationId);
    }

    @Transactional(readOnly = true)
    public WebhookDeliveryListResponse listDeliveriesConsole(ConsolePrincipal principal, UUID integrationId, int limit) {
        ConsoleAuth.require(principal, Permission.API_KEY_READ);
        WebhookEndpoint endpoint = endpoints
                .findByIntegrationIdAndOrganizationId(integrationId, principal.organizationId())
                .orElseThrow(() -> ApiException.notFound("Webhook endpoint not found"));
        int size = Math.min(Math.max(limit, 1), 50);
        List<WebhookDeliveryListResponse.Item> items = deliveries
                .findByEndpointIdOrderByCreatedAtDesc(endpoint.getId(), PageRequest.of(0, size))
                .stream()
                .map(d -> new WebhookDeliveryListResponse.Item(
                        d.getId(),
                        d.getVerificationId(),
                        Enums.json(d.getStatus()),
                        d.getAttempt(),
                        d.getHttpStatus(),
                        d.getCreatedAt()))
                .toList();
        return new WebhookDeliveryListResponse(items);
    }

    @Transactional
    public void retryBearer(ApiPrincipal principal, UUID eventId) {
        retry(principal.organizationId(), principal.integrationId(), eventId);
    }

    @Transactional
    public void retryConsole(ConsolePrincipal principal, UUID integrationId, UUID eventId) {
        ConsoleAuth.require(principal, Permission.API_KEY_WRITE);
        retry(principal.organizationId(), integrationId, eventId);
    }

    @Transactional
    public void enqueueCompleted(Verification verification, Instant now) {
        if (verification.getDecision() == null) {
            return;
        }
        WebhookEndpoint endpoint = endpoints.findByIntegrationId(verification.getIntegrationId()).orElse(null);
        if (endpoint == null || !endpoint.isActive()) {
            return;
        }
        String fingerprint = CryptoTokens.sha256Hex(
                verification.getId() + ":" + verification.getDecision() + ":" + verification.getUpdatedAt());
        if (deliveries.existsByVerificationIdAndEventTypeAndDecisionFingerprint(
                verification.getId(), WebhookEventType.VERIFICATION_COMPLETED, fingerprint)) {
            return;
        }
        UUID eventId = UUID.randomUUID();
        String payload = buildPayload(verification, eventId, now);
        deliveries.save(new WebhookDelivery(
                eventId,
                verification.getOrganizationId(),
                verification.getIntegrationId(),
                endpoint.getId(),
                verification.getId(),
                fingerprint,
                payload,
                CryptoTokens.sha256Hex(payload),
                now));
    }

    String decryptSecret(WebhookEndpoint endpoint) {
        return decrypt(endpoint.getSecretCipher());
    }

    static Instant nextAttemptAt(int attemptNumber, Instant now) {
        int index = Math.min(Math.max(attemptNumber, 1), RETRY_DELAYS.length) - 1;
        return now.plus(RETRY_DELAYS[index]);
    }

    private WebhookEndpointResponse upsert(
            UUID organizationId, String actorType, UUID actorId, Integration integration, String rawUrl) {
        String url = normalizeUrl(rawUrl, integration.isLive());
        Instant now = Instant.now();
        var existing = endpoints.findByIntegrationId(integration.getId());
        if (existing.isPresent()) {
            WebhookEndpoint endpoint = existing.get();
            endpoint.updateUrl(url, now);
            audit(organizationId, actorType, actorId, "webhook.upserted", endpoint.getId(), now);
            return new WebhookEndpointResponse(endpoint.getUrl(), endpoint.getSecretPrefix(), null, Enums.json(endpoint.getStatus()));
        }
        String secret = newSecret();
        WebhookEndpoint created = new WebhookEndpoint(
                UUID.randomUUID(),
                organizationId,
                integration.getId(),
                url,
                encrypt(secret),
                secretPrefix(secret),
                now);
        endpoints.save(created);
        audit(organizationId, actorType, actorId, "webhook.upserted", created.getId(), now);
        return new WebhookEndpointResponse(created.getUrl(), created.getSecretPrefix(), secret, Enums.json(created.getStatus()));
    }

    private WebhookEndpointResponse get(UUID organizationId, UUID integrationId) {
        WebhookEndpoint endpoint = endpoints
                .findByIntegrationIdAndOrganizationId(integrationId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Webhook endpoint not found"));
            return new WebhookEndpointResponse(
                    endpoint.getUrl(), endpoint.getSecretPrefix(), null, Enums.json(endpoint.getStatus()));
    }

    private void delete(UUID organizationId, String actorType, UUID actorId, UUID integrationId) {
        WebhookEndpoint endpoint = endpoints
                .findByIntegrationIdAndOrganizationId(integrationId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Webhook endpoint not found"));
        endpoints.delete(endpoint);
        audit(organizationId, actorType, actorId, "webhook.deleted", endpoint.getId(), Instant.now());
    }

    private WebhookEndpointResponse rotate(UUID organizationId, String actorType, UUID actorId, UUID integrationId) {
        WebhookEndpoint endpoint = endpoints
                .findByIntegrationIdAndOrganizationId(integrationId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Webhook endpoint not found"));
        Instant now = Instant.now();
        String secret = newSecret();
        endpoint.rotateSecret(encrypt(secret), secretPrefix(secret), now);
        audit(organizationId, actorType, actorId, "webhook.rotated", endpoint.getId(), now);
        return new WebhookEndpointResponse(
                endpoint.getUrl(), endpoint.getSecretPrefix(), secret, Enums.json(endpoint.getStatus()));
    }

    private void retry(UUID organizationId, UUID integrationId, UUID eventId) {
        WebhookDelivery delivery = deliveries
                .findByIdAndOrganizationId(eventId, organizationId)
                .filter(d -> d.getIntegrationId().equals(integrationId))
                .orElseThrow(() -> ApiException.notFound("Webhook delivery not found"));
        if (delivery.getStatus() != WebhookDeliveryStatus.FAILED
                && delivery.getStatus() != WebhookDeliveryStatus.DELIVERED) {
            throw ApiException.conflict("invalid_status", "Delivery cannot be retried");
        }
        delivery.requeue(Instant.now());
    }

    private Integration requireIntegration(UUID organizationId, UUID integrationId) {
        return integrations
                .findByIdAndOrganizationId(integrationId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Integration not found"));
    }

    private String normalizeUrl(String rawUrl, boolean live) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        try {
            WebhookSsrfGuard.validate(url, live);
        } catch (IllegalArgumentException e) {
            if ("ssrf_denied".equals(e.getMessage()) || (live && e.getMessage() != null && e.getMessage().contains("ssrf"))) {
                throw new ApiException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "ssrf_denied", "URL not allowed for live webhooks");
            }
            throw ApiException.validation("Invalid webhook URL", List.of(new ErrorDetail("url", "invalid")));
        }
        return url;
    }

    private String buildPayload(Verification verification, UUID eventId, Instant now) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", verification.getId().toString());
        data.put("integration_id", verification.getIntegrationId().toString());
        data.put("external_id", verification.getExternalId());
        data.put("status", Enums.json(verification.getStatus()));
        data.put("decision", Enums.json(verification.getDecision()));
        data.put("decision_reasons", parseReasons(verification.getDecisionReasons()));
        data.put("extracted_identity", parseExtracted(verification.getExtractedIdentity()));
        data.put("created_at", verification.getCreatedAt().toString());
        data.put("updated_at", verification.getUpdatedAt().toString());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", "evt_" + eventId.toString().replace("-", ""));
        envelope.put("type", "verification.completed");
        envelope.put("created_at", now.toString());
        envelope.put("data", data);
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode webhook payload", e);
        }
    }

    private List<String> parseReasons(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private Object parseExtracted(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.isEmpty() ? null : objectMapper.convertValue(node, Object.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private void audit(UUID organizationId, String actorType, UUID actorId, String action, UUID resourceId, Instant now) {
        auditEvents.save(new AuditEvent(
                organizationId, actorType, actorId, action, "webhook_endpoint", resourceId, "{}", now));
    }

    private static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "whsec_" + HexFormat.of().formatHex(bytes);
    }

    private static String secretPrefix(String secret) {
        return secret.substring(0, Math.min(12, secret.length()));
    }

    private String encrypt(String plaintext) {
        try {
            byte[] key = aesKey();
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt webhook secret", e);
        }
    }

    private String decrypt(String cipherText) {
        try {
            byte[] key = aesKey();
            byte[] all = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[12];
            byte[] payload = new byte[all.length - 12];
            System.arraycopy(all, 0, iv, 0, 12);
            System.arraycopy(all, 12, payload, 0, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt webhook secret", e);
        }
    }

    private byte[] aesKey() {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(properties.ipHashPepper().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
