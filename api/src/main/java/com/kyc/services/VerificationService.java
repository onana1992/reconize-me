package com.kyc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.config.KycProperties;
import com.kyc.dto.CreateVerificationRequest;
import com.kyc.dto.CreateVerificationRequest.ApplicantRequest;
import com.kyc.dto.VerificationListResponse;
import com.kyc.dto.VerificationResponse;
import com.kyc.dto.VerificationResponses;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.IdempotencyKey;
import com.kyc.entities.IdempotencyKeyId;
import com.kyc.entities.Verification;
import com.kyc.entities.VerificationStatus;
import com.kyc.ports.HostedTokenStore;
import com.kyc.ports.HostedTokenStore.HostedSession;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.IdempotencyKeyRepository;
import com.kyc.repositories.VerificationRepository;
import com.kyc.security.ApiPrincipal;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {};
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final VerificationRepository verificationRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AuditEventRepository auditEventRepository;
    private final HostedTokenStore hostedTokenStore;
    private final KycProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public VerificationService(
            VerificationRepository verificationRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            AuditEventRepository auditEventRepository,
            HostedTokenStore hostedTokenStore,
            KycProperties properties,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.verificationRepository = verificationRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.auditEventRepository = auditEventRepository;
        this.hostedTokenStore = hostedTokenStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public CreatedVerification create(ApiPrincipal principal, CreateVerificationRequest body, String idempotencyKey) {
        CreateVerificationRequest request = normalize(body);
        String requestHash = requestHash(request);
        String key = blankToNull(idempotencyKey);
        if (key != null) {
            validateIdempotencyKey(key);
        }

        Persisted persisted = transactionTemplate.execute(status -> persist(principal, request, requestHash, key));
        if (persisted.replayed()) {
            String hostedUrl = hostedUrlFor(persisted.verification().getId());
            return new CreatedVerification(toResponse(persisted.verification(), hostedUrl), true);
        }

        Duration ttl = Duration.ofSeconds(properties.hostedUrlTtlSeconds());
        try {
            hostedTokenStore.put(
                    persisted.token(),
                    new HostedSession(
                            persisted.verification().getId(),
                            persisted.verification().getOrganizationId(),
                            "created"),
                    ttl);
        } catch (RuntimeException e) {
            log.error(
                    "hosted token store unavailable organization_id={} verification_id={}",
                    principal.organizationId(),
                    persisted.verification().getId());
            throw ApiException.dependencyUnavailable("Hosted session store unavailable");
        }

        log.info(
                "verification created organization_id={} verification_id={}",
                principal.organizationId(),
                persisted.verification().getId());
        return new CreatedVerification(
                toResponse(persisted.verification(), properties.hostedUrl(persisted.token())), false);
    }

    public VerificationResponse getForOrganization(UUID id, UUID organizationId) {
        Verification verification = requireOwned(id, organizationId);
        return toResponse(verification, hostedUrlFor(verification.getId()));
    }

    public VerificationListResponse list(
            UUID organizationId, String status, String externalId, String cursor, Integer limit) {
        int pageSize = limit == null ? DEFAULT_LIMIT : limit;
        if (pageSize < 1 || pageSize > MAX_LIMIT) {
            throw ApiException.validation("limit is invalid", List.of(new ErrorDetail("limit", "range")));
        }
        VerificationStatus statusFilter = parseStatus(status);
        String externalIdFilter = blankToNull(externalId);
        boolean hasCursor = blankToNull(cursor) != null;
        Instant cursorCreatedAt = null;
        UUID cursorId = null;
        if (hasCursor) {
            ListCursor decoded = decodeCursor(cursor);
            cursorCreatedAt = decoded.createdAt();
            cursorId = decoded.id();
        }

        List<Verification> rows = verificationRepository.search(
                organizationId,
                statusFilter,
                externalIdFilter,
                hasCursor,
                cursorCreatedAt,
                cursorId,
                PageRequest.of(0, pageSize + 1));
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        String nextCursor = hasMore ? encodeCursor(rows.get(rows.size() - 1)) : null;
        List<VerificationResponse> data = rows.stream()
                .map(verification -> toResponse(verification, hostedUrlFor(verification.getId())))
                .toList();
        return new VerificationListResponse(data, nextCursor);
    }

    public VerificationResponse cancel(ApiPrincipal principal, UUID id) {
        Verification verification = transactionTemplate.execute(status -> {
            Verification owned = requireOwned(id, principal.organizationId());
            if (owned.getStatus() != VerificationStatus.CREATED
                    && owned.getStatus() != VerificationStatus.PENDING_CONSENT) {
                throw ApiException.conflict("invalid_status", "Verification cannot be cancelled");
            }
            Instant now = Instant.now();
            owned.transitionTo(VerificationStatus.CANCELLED, now);
            auditEventRepository.save(new AuditEvent(
                    principal.organizationId(),
                    "api_key",
                    principal.apiKeyId(),
                    "verification.cancelled",
                    "verification",
                    owned.getId(),
                    "{}",
                    now));
            return owned;
        });
        hostedTokenStore.delete(verification.getId());
        log.info(
                "verification cancelled organization_id={} verification_id={}",
                principal.organizationId(),
                verification.getId());
        return toResponse(verification, null);
    }

    private Persisted persist(
            ApiPrincipal principal, CreateVerificationRequest request, String requestHash, String idempotencyKey) {
        UUID organizationId = principal.organizationId();
        Instant now = Instant.now();

        if (idempotencyKey != null) {
            Optional<Persisted> replay = existingIdempotency(organizationId, idempotencyKey, requestHash, now);
            if (replay.isPresent()) {
                return replay.get();
            }
        }

        String externalId = request.externalId();
        if (externalId != null && verificationRepository.existsByOrganizationIdAndExternalId(organizationId, externalId)) {
            throw ApiException.conflict("external_id_conflict", "external_id already exists");
        }

        String token = CryptoTokens.randomHostedToken();
        String tokenHash = CryptoTokens.sha256Hex(token);
        Instant expiresAt = now.plusSeconds(properties.hostedUrlTtlSeconds());
        UUID verificationId = UUID.randomUUID();

        Verification verification = new Verification(
                verificationId,
                organizationId,
                externalId,
                VerificationStatus.CREATED,
                tokenHash,
                expiresAt,
                now);
        ApplicantRequest applicant = request.applicant();
        if (applicant != null) {
            verification.setApplicant(applicant.firstName(), applicant.lastName(), applicant.email());
        }
        verification.setMetadata(MetadataValidator.toJson(objectMapper, request.metadata()));

        try {
            verificationRepository.saveAndFlush(verification);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("external_id_conflict", "external_id already exists");
        }

        auditEventRepository.save(new AuditEvent(
                organizationId,
                "api_key",
                principal.apiKeyId(),
                "verification.created",
                "verification",
                verificationId,
                "{\"via\":\"api\"}",
                now));
        auditEventRepository.save(new AuditEvent(
                organizationId,
                "api_key",
                principal.apiKeyId(),
                "hosted_link.issued",
                "verification",
                verificationId,
                "{\"expires_at\":\"" + DateTimeFormatter.ISO_INSTANT.format(expiresAt) + "\"}",
                now));

        if (idempotencyKey != null) {
            try {
                idempotencyKeyRepository.saveAndFlush(new IdempotencyKey(
                        organizationId, idempotencyKey, requestHash, verificationId, now));
            } catch (DataIntegrityViolationException e) {
                return existingIdempotency(organizationId, idempotencyKey, requestHash, now)
                        .orElseThrow(() -> ApiException.conflict(
                                "idempotency_key_conflict", "Idempotency-Key was reused with a different body"));
            }
        }

        return new Persisted(verification, token, false);
    }

    private Optional<Persisted> existingIdempotency(
            UUID organizationId, String idempotencyKey, String requestHash, Instant now) {
        Optional<IdempotencyKey> existing =
                idempotencyKeyRepository.findById(new IdempotencyKeyId(organizationId, idempotencyKey));
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        IdempotencyKey row = existing.get();
        Instant expiresAt = row.getCreatedAt().plus(Duration.ofHours(properties.idempotencyTtlHours()));
        if (now.isAfter(expiresAt)) {
            idempotencyKeyRepository.delete(row);
            idempotencyKeyRepository.flush();
            return Optional.empty();
        }
        if (!requestHash.equals(row.getRequestHash())) {
            throw ApiException.conflict(
                    "idempotency_key_conflict", "Idempotency-Key was reused with a different body");
        }
        Verification verification = verificationRepository
                .findByIdAndOrganizationId(row.getVerificationId(), organizationId)
                .orElseThrow(() -> ApiException.conflict(
                        "idempotency_key_conflict", "Idempotency-Key was reused with a different body"));
        return Optional.of(new Persisted(verification, null, true));
    }

    private Verification requireOwned(UUID id, UUID organizationId) {
        return verificationRepository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> ApiException.notFound("Verification not found"));
    }

    private VerificationResponse toResponse(Verification verification, String hostedUrl) {
        return VerificationResponses.from(verification, hostedUrl, parseMetadata(verification.getMetadata()));
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, METADATA_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String hostedUrlFor(UUID verificationId) {
        return hostedTokenStore
                .findToken(verificationId)
                .map(properties::hostedUrl)
                .orElse(null);
    }

    private CreateVerificationRequest normalize(CreateVerificationRequest body) {
        if (body == null) {
            return new CreateVerificationRequest(null, null, Map.of());
        }
        Map<String, Object> metadata = MetadataValidator.requireValid(objectMapper, body.metadata());
        String externalId = blankToNull(body.externalId());
        ApplicantRequest applicant = body.applicant();
        if (applicant == null) {
            return new CreateVerificationRequest(externalId, null, metadata);
        }
        ApplicantRequest trimmed = new ApplicantRequest(
                blankToNull(trim(applicant.firstName())),
                blankToNull(trim(applicant.lastName())),
                blankToNull(trim(applicant.email())));
        if (trimmed.firstName() == null && trimmed.lastName() == null && trimmed.email() == null) {
            return new CreateVerificationRequest(externalId, null, metadata);
        }
        return new CreateVerificationRequest(externalId, trimmed, metadata);
    }

    private String requestHash(CreateVerificationRequest request) {
        try {
            return CryptoTokens.sha256Hex(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to hash request", e);
        }
    }

    private static VerificationStatus parseStatus(String status) {
        String value = blankToNull(status);
        if (value == null) {
            return null;
        }
        try {
            return VerificationStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("status is invalid", List.of(new ErrorDetail("status", "enum")));
        }
    }

    private static String encodeCursor(Verification verification) {
        String raw = verification.getCreatedAt() + "|" + verification.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static ListCursor decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = decoded.lastIndexOf('|');
            if (separator <= 0 || separator == decoded.length() - 1) {
                throw new IllegalArgumentException("cursor");
            }
            return new ListCursor(
                    Instant.parse(decoded.substring(0, separator)), UUID.fromString(decoded.substring(separator + 1)));
        } catch (RuntimeException e) {
            throw ApiException.validation("cursor is invalid", List.of(new ErrorDetail("cursor", "pattern")));
        }
    }

    private static void validateIdempotencyKey(String key) {
        if (key.length() < 8 || key.length() > 64 || !key.chars().allMatch(c -> c >= 32 && c <= 126)) {
            throw ApiException.validation(
                    "Idempotency-Key is invalid", List.of(new ErrorDetail("Idempotency-Key", "pattern")));
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    public record CreatedVerification(VerificationResponse response, boolean replayed) {}

    private record Persisted(Verification verification, String token, boolean replayed) {}

    private record ListCursor(Instant createdAt, UUID id) {}
}
