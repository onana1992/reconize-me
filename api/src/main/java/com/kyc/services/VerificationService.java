package com.kyc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.config.KycProperties;
import com.kyc.dto.idv.CreateVerificationRequest;
import com.kyc.dto.idv.MediaUrlResponse;
import com.kyc.dto.idv.VerificationListResponse;
import com.kyc.dto.idv.VerificationResponse;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.IdempotencyKey;
import com.kyc.entities.Integration;
import com.kyc.entities.Verification;
import com.kyc.entities.VerificationMedia;
import com.kyc.entities.VerificationSignal;
import com.kyc.ports.HostedTokenStore;
import com.kyc.ports.ObjectStoragePort;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.IdempotencyKeyRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.VerificationMediaRepository;
import com.kyc.repositories.VerificationRepository;
import com.kyc.repositories.VerificationSignalRepository;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private final VerificationRepository verifications;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final VerificationMediaRepository media;
    private final VerificationSignalRepository signals;
    private final AuditEventRepository auditEvents;
    private final IntegrationRepository integrationRepository;
    private final HostedTokenStore hostedTokens;
    private final ObjectStoragePort objectStorage;
    private final KycProperties properties;
    private final ObjectMapper objectMapper;

    public VerificationService(
            VerificationRepository verifications,
            IdempotencyKeyRepository idempotencyKeys,
            VerificationMediaRepository media,
            VerificationSignalRepository signals,
            AuditEventRepository auditEvents,
            IntegrationRepository integrationRepository,
            HostedTokenStore hostedTokens,
            ObjectStoragePort objectStorage,
            KycProperties properties,
            ObjectMapper objectMapper) {
        this.verifications = verifications;
        this.idempotencyKeys = idempotencyKeys;
        this.media = media;
        this.signals = signals;
        this.auditEvents = auditEvents;
        this.integrationRepository = integrationRepository;
        this.hostedTokens = hostedTokens;
        this.objectStorage = objectStorage;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResponseEntity<VerificationResponse> create(
            UUID organizationId,
            Integration integration,
            String actorType,
            UUID actorId,
            CreateVerificationRequest request,
            String idempotencyKey) {
        Instant now = Instant.now();
        String bodyHash = CryptoTokens.sha256Hex(serialize(request));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            validateIdempotencyKey(idempotencyKey);
            var existing = idempotencyKeys.findByOrganizationIdAndKey(organizationId, idempotencyKey);
            if (existing.isPresent()) {
                if (!existing.get().getRequestHash().equals(bodyHash)) {
                    throw ApiException.conflict("idempotency_key_conflict", "Idempotency key reused with a different body");
                }
                Verification replay = verifications
                        .findByIdAndOrganizationId(existing.get().getVerificationId(), organizationId)
                        .orElseThrow(() -> ApiException.notFound("Verification not found"));
                return ResponseEntity.ok(toResponse(replay, true));
            }
        }
        String externalId = blankToNull(request == null ? null : request.externalId());
        if (externalId != null && verifications.existsByOrganizationIdAndExternalId(organizationId, externalId)) {
            throw ApiException.conflict("external_id_conflict", "external_id already used in this organization");
        }
        Map<String, Object> metadata = request == null ? null : request.metadata();
        String metadataJson = validateMetadata(metadata);
        String scenario = integration.isLive() ? null : sandboxScenario(metadata);
        CreateVerificationRequest.Applicant applicant = request == null ? null : request.applicant();
        UUID id = UUID.randomUUID();
        String token = CryptoTokens.randomHostedToken();
        Duration ttl = Duration.ofSeconds(properties.hostedUrlTtlSeconds());
        Verification verification = new Verification(
                id,
                organizationId,
                integration.getId(),
                externalId,
                applicant == null ? null : blankToNull(applicant.firstName()),
                applicant == null ? null : blankToNull(applicant.lastName()),
                applicant == null ? null : blankToNull(applicant.email()),
                metadataJson,
                CryptoTokens.sha256Hex(token),
                now.plus(ttl),
                scenario,
                now);
        try {
            hostedTokens.put(token, organizationId, id, ttl);
        } catch (RuntimeException e) {
            throw ApiException.dependencyUnavailable("Hosted link store unavailable");
        }
        verifications.save(verification);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyKeys.save(new IdempotencyKey(organizationId, idempotencyKey, bodyHash, id, now));
        }
        audit(organizationId, actorType, actorId, "verification.created", "verification", id, now);
        audit(organizationId, actorType, actorId, "hosted_link.issued", "verification", id, now);
        return new ResponseEntity<>(toResponse(verification, token), HttpStatus.CREATED);
    }

    @Transactional(readOnly = true)
    public VerificationResponse get(UUID organizationId, UUID id) {
        Verification verification = verifications
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> ApiException.notFound("Verification not found"));
        return toResponse(verification, true);
    }

    @Transactional(readOnly = true)
    public VerificationListResponse list(
            UUID organizationId, String status, UUID integrationId, String cursor, int limit) {
        int size = Math.min(Math.max(limit, 1), 100);
        List<Verification> page;
        if (cursor == null || cursor.isBlank()) {
            page = verifications.pageFirst(organizationId, blankToNull(status), integrationId, PageRequest.of(0, size + 1));
        } else {
            Cursor decoded = Cursor.parse(cursor);
            page = verifications.pageAfter(
                    organizationId,
                    blankToNull(status),
                    integrationId,
                    decoded.createdAt(),
                    decoded.id(),
                    PageRequest.of(0, size + 1));
        }
        String next = null;
        if (page.size() > size) {
            page = new ArrayList<>(page.subList(0, size));
            Verification last = page.get(page.size() - 1);
            next = new Cursor(last.getCreatedAt(), last.getId()).encode();
        }
        Map<UUID, Integration> integrationsById = loadIntegrations(page);
        return new VerificationListResponse(
                page.stream()
                        .map(item -> toResponse(item, false, modeOf(item, integrationsById)))
                        .toList(),
                next);
    }

    @Transactional
    public VerificationResponse cancel(UUID organizationId, String actorType, UUID actorId, UUID id) {
        Verification verification = verifications
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> ApiException.notFound("Verification not found"));
        if (!verification.cancelable()) {
            throw ApiException.conflict("invalid_status", "Verification cannot be cancelled");
        }
        Instant now = Instant.now();
        verification.cancel(now);
        hostedTokens.revokeByVerificationId(id);
        audit(organizationId, actorType, actorId, "verification.cancelled", "verification", id, now);
        return toResponse(verification, true);
    }

    @Transactional
    public VerificationResponse review(UUID organizationId, String actorType, UUID actorId, UUID id, String decision) {
        if (!"approved".equals(decision) && !"declined".equals(decision)) {
            throw ApiException.validation("Invalid review decision", List.of(new ErrorDetail("decision", "invalid")));
        }
        Verification verification = verifications
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> ApiException.notFound("Verification not found"));
        if (!Verification.REVIEW.equals(verification.getStatus())) {
            throw ApiException.conflict("invalid_status", "Verification is not in review");
        }
        Instant now = Instant.now();
        verification.applyReview(decision, now);
        audit(organizationId, actorType, actorId, "verification.reviewed", "verification", id, "{\"decision\":\"" + decision + "\"}", now);
        return toResponse(verification, true);
    }

    @Transactional(readOnly = true)
    public MediaUrlResponse mediaUrl(UUID organizationId, UUID id, String kind) {
        Verification verification = verifications
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> ApiException.notFound("Verification not found"));
        VerificationMedia item = media.findFirstByVerificationIdAndKindAndStatusOrderByAttemptDesc(
                        verification.getId(), kind, VerificationMedia.ACCEPTED)
                .orElseThrow(() -> ApiException.notFound("Media not found"));
        var signed = objectStorage.createSignedGetUrl(item.getObjectKey(), Duration.ofMinutes(5));
        return new MediaUrlResponse(signed.url(), signed.expiresAt());
    }

    public VerificationResponse toResponse(Verification verification, boolean detail) {
        String token = hostedTokens.tokenFor(verification.getId()).orElse(null);
        return toResponse(verification, token, detail, modeFor(verification));
    }

    private VerificationResponse toResponse(Verification verification, boolean detail, String mode) {
        String token = hostedTokens.tokenFor(verification.getId()).orElse(null);
        return toResponse(verification, token, detail, mode);
    }

    private VerificationResponse toResponse(Verification verification, String rawToken) {
        return toResponse(verification, rawToken, true, modeFor(verification));
    }

    private VerificationResponse toResponse(Verification verification, String rawToken, boolean detail, String mode) {
        String hostedUrl = null;
        if (rawToken != null
                && !verification.expired(Instant.now())
                && !Verification.CANCELLED.equals(verification.getStatus())
                && !Verification.EXPIRED.equals(verification.getStatus())) {
            hostedUrl = properties.hostedUrl(rawToken);
        }
        List<VerificationResponse.Signal> signalItems = List.of();
        Map<String, Object> extracted = null;
        if (detail) {
            signalItems = signals.findByVerificationIdOrderByCreatedAtAsc(verification.getId()).stream()
                    .map(item -> new VerificationResponse.Signal(item.getCode(), item.getOutcome(), item.getScore()))
                    .toList();
            extracted = parseMap(verification.getExtractedIdentity());
        }
        return new VerificationResponse(
                verification.getId(),
                verification.getStatus(),
                verification.getIntegrationId(),
                mode,
                hostedUrl,
                verification.getHostedExpiresAt(),
                new VerificationResponse.Applicant(
                        verification.getApplicantFirstName(),
                        verification.getApplicantLastName(),
                        verification.getApplicantEmail()),
                parseMap(verification.getMetadata()),
                verification.getDecision(),
                parseStrings(verification.getDecisionReasons()),
                signalItems.isEmpty() ? null : signalItems,
                extracted,
                verification.getCreatedAt(),
                verification.getUpdatedAt());
    }

    private Map<UUID, Integration> loadIntegrations(List<Verification> page) {
        Set<UUID> ids = page.stream().map(Verification::getIntegrationId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return integrationRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Integration::getId, Function.identity()));
    }

    private static String modeOf(Verification verification, Map<UUID, Integration> byId) {
        Integration row = byId.get(verification.getIntegrationId());
        return row == null ? Integration.MODE_TEST : row.getMode();
    }

    private String modeFor(Verification verification) {
        return integrationRepository
                .findById(verification.getIntegrationId())
                .map(Integration::getMode)
                .orElse(Integration.MODE_TEST);
    }

    private String validateMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        String json = serialize(metadata);
        if (json.length() > 4096) {
            throw ApiException.validation("metadata too large", List.of(new ErrorDetail("metadata", "size")));
        }
        if (depth(metadata, 1) > 2) {
            throw ApiException.validation("metadata too deep", List.of(new ErrorDetail("metadata", "depth")));
        }
        return json;
    }

    private static int depth(Object node, int current) {
        if (node instanceof Map<?, ?> map) {
            int max = current;
            for (Object value : map.values()) {
                max = Math.max(max, depth(value, current + 1));
            }
            return max;
        }
        if (node instanceof List<?> list) {
            int max = current;
            for (Object value : list) {
                max = Math.max(max, depth(value, current + 1));
            }
            return max;
        }
        return current;
    }

    private static String sandboxScenario(Map<String, Object> metadata) {
        if (metadata == null) {
            return "approved";
        }
        Object nested = metadata.get("sandbox_scenario");
        if (nested instanceof String value && !value.isBlank()) {
            return value.trim();
        }
        Object sandbox = metadata.get("sandbox");
        if (sandbox instanceof Map<?, ?> map) {
            Object scenario = map.get("scenario");
            if (scenario instanceof String value && !value.isBlank()) {
                return value.trim();
            }
        }
        return "approved";
    }

    private static void validateIdempotencyKey(String key) {
        if (key.length() < 8 || key.length() > 64 || !key.chars().allMatch(ch -> ch >= 32 && ch <= 126)) {
            throw ApiException.validation("Invalid Idempotency-Key", List.of(new ErrorDetail("Idempotency-Key", "invalid")));
        }
    }

    private void audit(UUID organizationId, String actorType, UUID actorId, String action, String resourceType, UUID resourceId, Instant now) {
        audit(organizationId, actorType, actorId, action, resourceType, resourceId, "{}", now);
    }

    private void audit(
            UUID organizationId,
            String actorType,
            UUID actorId,
            String action,
            String resourceType,
            UUID resourceId,
            String payload,
            Instant now) {
        auditEvents.save(new AuditEvent(organizationId, actorType, actorId, action, resourceType, resourceId, payload, now));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw ApiException.validation("Invalid JSON", List.of());
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<String> parseStrings(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, STRINGS);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Cursor(Instant createdAt, UUID id) {
        String encode() {
            return createdAt.toEpochMilli() + "_" + id;
        }

        static Cursor parse(String raw) {
            int split = raw.indexOf('_');
            if (split < 1) {
                throw ApiException.validation("Invalid cursor", List.of(new ErrorDetail("cursor", "invalid")));
            }
            try {
                return new Cursor(
                        Instant.ofEpochMilli(Long.parseLong(raw.substring(0, split))), UUID.fromString(raw.substring(split + 1)));
            } catch (RuntimeException e) {
                throw ApiException.validation("Invalid cursor", List.of(new ErrorDetail("cursor", "invalid")));
            }
        }
    }
}
