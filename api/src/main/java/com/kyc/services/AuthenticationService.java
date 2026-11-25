package com.kyc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.config.KycProperties;
import com.kyc.dto.auth.AuthenticationListResponse;
import com.kyc.dto.auth.AuthenticationResponse;
import com.kyc.dto.auth.CreateAuthenticationRequest;
import com.kyc.dto.idv.MediaUrlResponse;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.AuthenticationIdempotencyKey;
import com.kyc.entities.AuthenticationMedia;
import com.kyc.entities.AuthenticationSession;
import com.kyc.entities.Enrollment;
import com.kyc.entities.Integration;
import com.kyc.enums.AuthenticationStatus;
import com.kyc.enums.EnrollmentStatus;
import com.kyc.enums.Enums;
import com.kyc.enums.MediaKind;
import com.kyc.enums.MediaStatus;
import com.kyc.enums.ProductCode;
import com.kyc.ports.HostedTokenStore;
import com.kyc.ports.ObjectStoragePort;
import com.kyc.enums.IntegrationMode;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.AuthenticationIdempotencyRepository;
import com.kyc.repositories.AuthenticationMediaRepository;
import com.kyc.repositories.AuthenticationRepository;
import com.kyc.repositories.EnrollmentRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.VerificationMediaRepository;
import com.kyc.services.auth.AuthPolicy;
import com.kyc.services.auth.AuthPolicyService;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private static final String ENGINE = "auth-2";
    private static final int METADATA_MAX = 4096;

    private final AuthenticationRepository authentications;
    private final AuthenticationIdempotencyRepository idempotency;
    private final EnrollmentRepository enrollments;
    private final AuthenticationMediaRepository media;
    private final AuditEventRepository auditEvents;
    private final HostedTokenStore hostedTokens;
    private final ObjectStoragePort objectStorage;
    private final CreditService credits;
    private final KycProperties properties;
    private final ObjectMapper objectMapper;
    private final IntegrationRepository integrations;
    private final AuthPolicyService policies;
    private final VerificationMediaRepository verificationMedia;
    private final AuthenticationCaptureStore captures;

    public AuthenticationService(
            AuthenticationRepository authentications,
            AuthenticationIdempotencyRepository idempotency,
            EnrollmentRepository enrollments,
            AuthenticationMediaRepository media,
            AuditEventRepository auditEvents,
            HostedTokenStore hostedTokens,
            ObjectStoragePort objectStorage,
            CreditService credits,
            KycProperties properties,
            ObjectMapper objectMapper,
            IntegrationRepository integrations,
            AuthPolicyService policies,
            VerificationMediaRepository verificationMedia,
            AuthenticationCaptureStore captures) {
        this.authentications = authentications;
        this.idempotency = idempotency;
        this.enrollments = enrollments;
        this.media = media;
        this.auditEvents = auditEvents;
        this.hostedTokens = hostedTokens;
        this.objectStorage = objectStorage;
        this.credits = credits;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.integrations = integrations;
        this.policies = policies;
        this.verificationMedia = verificationMedia;
        this.captures = captures;
    }

    @Transactional
    public ResponseEntity<AuthenticationResponse> create(
            Integration integration, String actorType, UUID actorId, CreateAuthenticationRequest request, String idempotencyKey) {
        requireAuthentication(integration);
        rejectSandboxScenario(request);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.validation(
                    "Idempotency-Key is required", List.of(new ErrorDetail("Idempotency-Key", "required")));
        }
        validateIdempotencyKey(idempotencyKey);
        Instant now = Instant.now();
        String bodyHash = CryptoTokens.sha256Hex(serialize(request));
        var existing = idempotency.findByOrganizationIdAndKey(integration.getOrganizationId(), idempotencyKey);
        if (existing.isPresent()) {
            AuthenticationIdempotencyKey stored = existing.get();
            if (stored.getCreatedAt().plus(Duration.ofHours(properties.idempotencyTtlHours())).isAfter(now)) {
                if (!stored.getRequestHash().equals(bodyHash)) {
                    throw ApiException.conflict(
                            "idempotency_key_conflict", "Idempotency key reused with a different body");
                }
                AuthenticationSession replay = require(integration, stored.getAuthenticationId());
                return ResponseEntity.ok(toResponse(replay, integration, true));
            }
            idempotency.delete(stored);
            idempotency.flush();
        }

        String externalId = request == null ? null : blankToNull(request.externalId());
        if (externalId == null) {
            throw ApiException.validation(
                    "external_id is required", List.of(new ErrorDetail("external_id", "required")));
        }
        if (externalId.length() > 128) {
            throw ApiException.validation("Invalid external_id", List.of(new ErrorDetail("external_id", "size")));
        }
        String metadataJson = validateMetadata(request == null ? null : request.metadata());

        Enrollment enrollment = enrollments
                .findByOrganizationIdAndExternalIdAndStatus(
                        integration.getOrganizationId(), externalId, EnrollmentStatus.ACTIVE)
                .orElseThrow(() -> ApiException.conflict("no_enrollment", "No active enrollment for this external_id"));

        if (!credits.covers(integration.getOrganizationId(), properties.billing().authenticationAmountMinor())) {
            throw ApiException.forbidden("insufficient_credit", "Authentication requires a credit balance");
        }

        AuthPolicy policy = policies.effective(integration);
        UUID id = UUID.randomUUID();
        String token = CryptoTokens.randomHostedToken();
        Duration ttl = Duration.ofSeconds(properties.hostedUrlTtlSeconds());
        AuthenticationSession session = new AuthenticationSession(
                id,
                integration.getOrganizationId(),
                integration.getId(),
                externalId,
                enrollment.getId(),
                enrollment.getFaceId(),
                ENGINE,
                policy.version(),
                metadataJson,
                CryptoTokens.sha256Hex(token),
                now.plus(ttl),
                now);
        try {
            hostedTokens.put(token, integration.getOrganizationId(), id, ProductCode.AUTHENTICATION, ttl);
        } catch (RuntimeException e) {
            throw ApiException.dependencyUnavailable("Hosted link store unavailable");
        }
        authentications.save(session);
        idempotency.save(new AuthenticationIdempotencyKey(
                integration.getOrganizationId(), idempotencyKey, bodyHash, id, now));
        auditEvents.save(new AuditEvent(
                integration.getOrganizationId(),
                actorType,
                actorId,
                "authentication.created",
                "authentication",
                id,
                "{}",
                now));
        return new ResponseEntity<>(toResponse(session, integration, token), HttpStatus.CREATED);
    }

    @Transactional(readOnly = true)
    public AuthenticationResponse get(Integration integration, UUID id) {
        requireAuthentication(integration);
        return toResponse(require(integration, id), integration, true);
    }

    @Transactional(readOnly = true)
    public AuthenticationListResponse list(
            Integration integration, String status, String externalId, String cursor, int limit) {
        requireAuthentication(integration);
        int size = Math.min(Math.max(limit, 1), 100);
        AuthenticationStatus parsed = parseStatus(status);
        String external = blankToNull(externalId);
        List<AuthenticationSession> page;
        if (cursor == null || cursor.isBlank()) {
            page = authentications.pageFirst(
                    integration.getOrganizationId(),
                    integration.getId(),
                    parsed,
                    external,
                    PageRequest.of(0, size + 1));
        } else {
            Cursor decoded = Cursor.parse(cursor);
            page = authentications.pageAfter(
                    integration.getOrganizationId(),
                    integration.getId(),
                    parsed,
                    external,
                    decoded.createdAt(),
                    decoded.id(),
                    PageRequest.of(0, size + 1));
        }
        String next = null;
        if (page.size() > size) {
            page = new ArrayList<>(page.subList(0, size));
            AuthenticationSession last = page.get(page.size() - 1);
            next = new Cursor(last.getCreatedAt(), last.getId()).encode();
        }
        List<AuthenticationResponse> data =
                page.stream().map(row -> toResponse(row, integration, false)).toList();
        return new AuthenticationListResponse(data, next);
    }

    @Transactional(readOnly = true)
    public AuthenticationListResponse listForOrganization(
            UUID organizationId, String status, UUID integrationId, String cursor, int limit) {
        int size = Math.min(Math.max(limit, 1), 100);
        AuthenticationStatus parsed = parseStatus(status);
        List<AuthenticationSession> page;
        if (cursor == null || cursor.isBlank()) {
            page = authentications.pageOrgFirst(organizationId, parsed, integrationId, PageRequest.of(0, size + 1));
        } else {
            Cursor decoded = Cursor.parse(cursor);
            page = authentications.pageOrgAfter(
                    organizationId,
                    parsed,
                    integrationId,
                    decoded.createdAt(),
                    decoded.id(),
                    PageRequest.of(0, size + 1));
        }
        String next = null;
        if (page.size() > size) {
            page = new ArrayList<>(page.subList(0, size));
            AuthenticationSession last = page.get(page.size() - 1);
            next = new Cursor(last.getCreatedAt(), last.getId()).encode();
        }
        Map<UUID, Integration> byId = loadIntegrations(page);
        List<AuthenticationResponse> data =
                page.stream().map(row -> toResponse(row, byId.get(row.getIntegrationId()), false)).toList();
        return new AuthenticationListResponse(data, next);
    }

    @Transactional
    public AuthenticationResponse getForOrganization(UUID organizationId, UUID id) {
        AuthenticationSession session = authentications
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> ApiException.notFound("Authentication not found"));
        if (session.terminal()) {
            captures.discardSessionSelfies(session.getId());
        }
        Integration integration = integrations.findById(session.getIntegrationId()).orElse(null);
        return toResponse(session, integration, true);
    }

    @Transactional
    public AuthenticationResponse cancel(Integration integration, String actorType, UUID actorId, UUID id) {
        requireAuthentication(integration);
        AuthenticationSession session = require(integration, id);
        if (!session.cancelable()) {
            throw ApiException.conflict("invalid_status", "Authentication cannot be cancelled");
        }
        Instant now = Instant.now();
        session.cancel(now);
        hostedTokens.revokeByVerificationId(id);
        authentications.save(session);
        captures.discardSessionSelfies(id);
        auditEvents.save(new AuditEvent(
                integration.getOrganizationId(),
                actorType,
                actorId,
                "authentication.cancelled",
                "authentication",
                id,
                "{}",
                now));
        return toResponse(session, integration, true);
    }

    @Transactional
    public AuthenticationResponse review(
            Integration integration, String actorType, UUID actorId, UUID id, String decision) {
        requireAuthentication(integration);
        throw ApiException.conflict("invalid_status", "Authentication has no review");
    }

    @Transactional
    public MediaUrlResponse media(Integration integration, UUID id, String kind) {
        requireAuthentication(integration);
        AuthenticationSession session = require(integration, id);
        if (session.terminal()) {
            captures.discardSessionSelfies(session.getId());
        }
        return sign(session, kind);
    }

    @Transactional
    public MediaUrlResponse mediaForOrganization(UUID organizationId, UUID id, String kind) {
        AuthenticationSession session = authentications
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> ApiException.notFound("Authentication not found"));
        if (session.terminal()) {
            captures.discardSessionSelfies(session.getId());
        }
        return sign(session, kind);
    }

    private MediaUrlResponse sign(AuthenticationSession session, String kind) {
        String normalized = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        String objectKey;
        if ("selfie".equals(normalized)) {
            AuthenticationMedia item = media.findFirstByAuthenticationIdAndKindAndStatusOrderByAttemptDesc(
                            session.getId(), MediaKind.SELFIE, MediaStatus.ACCEPTED)
                    .orElseThrow(() -> ApiException.notFound("Media not found"));
            objectKey = item.getObjectKey();
        } else if ("reference".equals(normalized)) {
            Enrollment enrollment = enrollments
                    .findByIdAndOrganizationId(session.getEnrollmentId(), session.getOrganizationId())
                    .orElseThrow(() -> ApiException.notFound("Media not found"));
            objectKey = verificationMedia
                    .findFirstByVerificationIdAndKindAndStatusOrderByAttemptDesc(
                            enrollment.getSourceVerificationId(), MediaKind.SELFIE, MediaStatus.ACCEPTED)
                    .orElseThrow(() -> ApiException.notFound("Media not found"))
                    .getObjectKey();
        } else {
            throw ApiException.notFound("Media not found");
        }
        var signed = objectStorage.createSignedGetUrl(objectKey, Duration.ofMinutes(5));
        return new MediaUrlResponse(signed.url(), signed.expiresAt());
    }

    private String reasonsJson(List<String> reasons) {
        try {
            return objectMapper.writeValueAsString(reasons);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private AuthenticationSession require(Integration integration, UUID id) {
        return authentications
                .findByIdAndOrganizationIdAndIntegrationId(
                        id, integration.getOrganizationId(), integration.getId())
                .orElseThrow(() -> ApiException.notFound("Authentication not found"));
    }

    private static void requireAuthentication(Integration integration) {
        if (integration.getProduct() != ProductCode.AUTHENTICATION || !integration.isLive()) {
            throw ApiException.unauthorized("unauthorized", "API key is not valid");
        }
    }

    private static void rejectSandboxScenario(CreateAuthenticationRequest request) {
        if (request == null) {
            return;
        }
        if (request.sandboxScenario() != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid",
                    "sandbox_scenario is not supported",
                    List.of(new ErrorDetail("sandbox_scenario", "invalid")));
        }
        Map<String, Object> metadata = request.metadata();
        if (metadata != null && metadata.containsKey("sandbox_scenario")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid",
                    "sandbox_scenario is not supported",
                    List.of(new ErrorDetail("sandbox_scenario", "invalid")));
        }
    }

    private String validateMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        String json = serialize(metadata);
        if (json.length() > METADATA_MAX) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid",
                    "metadata too large",
                    List.of(new ErrorDetail("metadata", "invalid")));
        }
        if (depth(metadata, 1) > 2) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid",
                    "metadata too deep",
                    List.of(new ErrorDetail("metadata", "invalid")));
        }
        return json;
    }

    private AuthenticationResponse toResponse(AuthenticationSession session, Integration integration, boolean detail) {
        String token = detail ? hostedTokens.tokenFor(session.getId()).orElse(null) : null;
        return toResponse(session, integration, token);
    }

    private AuthenticationResponse toResponse(AuthenticationSession session, Integration integration, String rawToken) {
        String hostedUrl = null;
        if (rawToken != null
                && !session.terminal()
                && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(Instant.now())) {
            hostedUrl = properties.hostedUrl(rawToken);
        }
        return new AuthenticationResponse(
                session.getId(),
                session.getIntegrationId(),
                Enums.json(integration == null ? IntegrationMode.LIVE : integration.getMode()),
                session.getExternalId(),
                session.getEnrollmentId(),
                Enums.json(session.getStatus()),
                readReasons(session.getReasons()),
                session.getFailureReason(),
                session.getMatchScore(),
                session.getEngine(),
                session.getPolicyVersion(),
                readMetadata(session.getMetadata()),
                session.getConsentVersion(),
                hostedUrl,
                session.getExpiresAt(),
                session.getCreatedAt(),
                session.getDecidedAt());
    }

    private Map<UUID, Integration> loadIntegrations(List<AuthenticationSession> page) {
        Set<UUID> ids = page.stream()
                .map(AuthenticationSession::getIntegrationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return integrations.findAllById(ids).stream().collect(Collectors.toMap(Integration::getId, Function.identity()));
    }

    private List<String> readReasons(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private Map<String, Object> readMetadata(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
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

    private static void validateIdempotencyKey(String key) {
        if (key.length() < 8 || key.length() > 64 || !key.chars().allMatch(ch -> ch >= 32 && ch <= 126)) {
            throw ApiException.validation(
                    "Invalid Idempotency-Key", List.of(new ErrorDetail("Idempotency-Key", "invalid")));
        }
    }

    private static AuthenticationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AuthenticationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("Invalid status", List.of(new ErrorDetail("status", "invalid")));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Cursor(Instant createdAt, UUID id) {
        String encode() {
            String raw = createdAt.toString() + "|" + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        static Cursor parse(String cursor) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int split = raw.indexOf('|');
                return new Cursor(Instant.parse(raw.substring(0, split)), UUID.fromString(raw.substring(split + 1)));
            } catch (RuntimeException e) {
                throw ApiException.validation("Invalid cursor", List.of(new ErrorDetail("cursor", "invalid")));
            }
        }
    }
}
