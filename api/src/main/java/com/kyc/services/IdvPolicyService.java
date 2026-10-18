package com.kyc.services;

import com.kyc.config.KycProperties;
import com.kyc.dto.documentia.DocumentIaCatalogResponse;
import com.kyc.dto.idv.IdvPolicyResponse;
import com.kyc.dto.idv.IdvPolicyVersionList;
import com.kyc.dto.idv.PublishIdvPolicyRequest;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.IdvPolicy;
import com.kyc.entities.IdvPolicyDocument;
import com.kyc.entities.User;
import com.kyc.enums.VerificationDecision;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.IdvPolicyDocumentRepository;
import com.kyc.repositories.IdvPolicyRepository;
import com.kyc.repositories.UserRepository;
import com.kyc.services.IdvDecisionEngine.DecisionProfile;
import com.kyc.services.documentia.SchemaRegistry;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdvPolicyService {

    public static final double OCR_MIN = IdvDecisionEngine.LOW_OCR_CONFIDENCE;
    public static final double OCR_MAX = 0.95;
    public static final double LIVENESS_MIN = IdvDecisionEngine.LIVENESS_PASS;
    public static final double LIVENESS_MAX = 0.99;
    public static final double FACE_FAIL_MIN = IdvDecisionEngine.FACE_MATCH_FAIL;
    public static final double FACE_FAIL_MAX = 0.98;
    public static final double FACE_PASS_MIN = IdvDecisionEngine.FACE_MATCH_PASS;
    public static final double FACE_PASS_MAX = 0.99;
    public static final int TTL_MIN = 900;
    public static final int TTL_MAX = 259_200;
    public static final int ATTEMPTS_MIN = 1;
    public static final int ATTEMPTS_MAX = 5;
    public static final int PLATFORM_ATTEMPTS = 3;

    private static final IdvPolicyResponse.Bounds BOUNDS = new IdvPolicyResponse.Bounds(
            new double[] {OCR_MIN, OCR_MAX},
            new double[] {LIVENESS_MIN, LIVENESS_MAX},
            new double[] {FACE_FAIL_MIN, FACE_FAIL_MAX},
            new double[] {FACE_PASS_MIN, FACE_PASS_MAX},
            new int[] {TTL_MIN, TTL_MAX},
            new int[] {ATTEMPTS_MIN, ATTEMPTS_MAX},
            new int[] {ATTEMPTS_MIN, ATTEMPTS_MAX});

    private final IdvPolicyRepository policies;
    private final IdvPolicyDocumentRepository documents;
    private final SchemaRegistry registry;
    private final AuditEventRepository auditEvents;
    private final UserRepository users;
    private final KycProperties properties;

    public IdvPolicyService(
            IdvPolicyRepository policies,
            IdvPolicyDocumentRepository documents,
            SchemaRegistry registry,
            AuditEventRepository auditEvents,
            UserRepository users,
            KycProperties properties) {
        this.policies = policies;
        this.documents = documents;
        this.registry = registry;
        this.auditEvents = auditEvents;
        this.users = users;
        this.properties = properties;
    }

    public record Creation(int linkTtlSeconds, UUID policyId, Integer policyVersion) {}

    public record Applied(
            DecisionProfile profile,
            Set<String> acceptedCodes,
            int maxCaptureAttempts,
            int maxLivenessAttempts,
            boolean reviewOnCaptureExhausted) {}

    @Transactional(readOnly = true)
    public IdvPolicyResponse effective(UUID organizationId) {
        return policies
                .findByOrganizationIdAndActiveTrue(organizationId)
                .map(policy -> toResponse(policy, codesOf(policy.getId())))
                .orElseGet(this::platformResponse);
    }

    @Transactional(readOnly = true)
    public IdvPolicyVersionList versions(UUID organizationId) {
        List<IdvPolicyVersionList.Item> items = policies.findByOrganizationIdOrderByVersionDesc(organizationId).stream()
                .map(policy -> new IdvPolicyVersionList.Item(policy.getVersion(), policy.getPublishedAt(), policy.isActive()))
                .toList();
        return new IdvPolicyVersionList(items);
    }

    @Transactional(readOnly = true)
    public Creation creation(UUID organizationId) {
        return policies
                .findByOrganizationIdAndActiveTrue(organizationId)
                .map(policy -> new Creation(policy.getLinkTtlSeconds(), policy.getId(), policy.getVersion()))
                .orElseGet(() -> new Creation(properties.hostedUrlTtlSeconds(), null, null));
    }

    @Transactional(readOnly = true)
    public Applied applied(UUID policyId) {
        if (policyId == null) {
            return platformApplied();
        }
        return policies.findById(policyId).map(this::appliedOf).orElseGet(this::platformApplied);
    }

    @Transactional
    public IdvPolicyResponse publish(UUID organizationId, UUID userId, PublishIdvPolicyRequest request) {
        Checked checked = check(request);
        Optional<IdvPolicy> current = policies.findByOrganizationIdAndActiveTrue(organizationId);
        int next = current.map(policy -> policy.getVersion() + 1).orElse(1);
        if (current.isPresent()) {
            current.get().retire();
            policies.saveAndFlush(current.get());
        }
        Instant now = Instant.now();
        IdvPolicy policy = new IdvPolicy(
                UUID.randomUUID(),
                organizationId,
                next,
                checked.expired(),
                checked.attempts(),
                scale(checked.ocr()),
                scale(checked.liveness()),
                scale(checked.faceFail()),
                scale(checked.facePass()),
                checked.ttl(),
                checked.captures(),
                checked.livenessAttempts(),
                userId,
                now);
        policies.save(policy);
        for (String code : checked.codes()) {
            documents.save(new IdvPolicyDocument(policy.getId(), code));
        }
        auditEvents.save(new AuditEvent(
                organizationId,
                "user",
                userId,
                "idv.policy.published",
                "idv_policy",
                policy.getId(),
                "{\"version\":" + next + "}",
                now));
        return toResponse(policy, checked.codes());
    }

    private Applied appliedOf(IdvPolicy policy) {
        return new Applied(
                new DecisionProfile(
                        policy.getExpiredOutcome(),
                        policy.getOcrReviewAt().doubleValue(),
                        policy.getLivenessPassAt().doubleValue(),
                        policy.getFaceMatchFailAt().doubleValue(),
                        policy.getFaceMatchPassAt().doubleValue()),
                codesOf(policy.getId()),
                policy.getMaxCaptureAttempts(),
                policy.getMaxLivenessAttempts(),
                policy.getAttemptsOutcome() == VerificationDecision.REVIEW);
    }

    private Applied platformApplied() {
        return new Applied(
                DecisionProfile.platform(),
                null,
                PLATFORM_ATTEMPTS,
                PLATFORM_ATTEMPTS,
                false);
    }

    private IdvPolicyResponse platformResponse() {
        DecisionProfile profile = DecisionProfile.platform();
        return new IdvPolicyResponse(
                "platform",
                null,
                null,
                null,
                new IdvPolicyResponse.Profile(
                        profile.expiredOutcome().name(),
                        VerificationDecision.DECLINED.name(),
                        profile.ocrReviewAt(),
                        profile.livenessPassAt(),
                        profile.faceMatchFailAt(),
                        profile.faceMatchPassAt()),
                catalog(null),
                new IdvPolicyResponse.Flow(
                        properties.hostedUrlTtlSeconds(),
                        PLATFORM_ATTEMPTS,
                        PLATFORM_ATTEMPTS,
                        properties.consentTextVersion()),
                BOUNDS);
    }

    private IdvPolicyResponse toResponse(IdvPolicy policy, Set<String> codes) {
        return new IdvPolicyResponse(
                "organization",
                policy.getVersion(),
                policy.getPublishedAt(),
                publisherName(policy.getPublishedBy()),
                new IdvPolicyResponse.Profile(
                        policy.getExpiredOutcome().name(),
                        policy.getAttemptsOutcome().name(),
                        policy.getOcrReviewAt().doubleValue(),
                        policy.getLivenessPassAt().doubleValue(),
                        policy.getFaceMatchFailAt().doubleValue(),
                        policy.getFaceMatchPassAt().doubleValue()),
                catalog(codes),
                new IdvPolicyResponse.Flow(
                        policy.getLinkTtlSeconds(),
                        policy.getMaxCaptureAttempts(),
                        policy.getMaxLivenessAttempts(),
                        properties.consentTextVersion()),
                BOUNDS);
    }

    private Set<String> codesOf(UUID policyId) {
        Set<String> codes = new LinkedHashSet<>();
        for (IdvPolicyDocument row : documents.findByPolicyId(policyId)) {
            codes.add(row.getDefinitionCode());
        }
        return Set.copyOf(codes);
    }

    private String publisherName(UUID userId) {
        return users.findById(userId).map(IdvPolicyService::nameOf).orElse(null);
    }

    private static String nameOf(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String name = (first + " " + last).trim();
        return name.isBlank() ? user.getEmail() : name;
    }

    private List<IdvPolicyResponse.Document> catalog(Set<String> enabled) {
        Map<String, CatalogRow> grouped = new LinkedHashMap<>();
        for (DocumentIaCatalogResponse.Schema schema : registry.catalog().schemas()) {
            grouped.compute(schema.code(), (code, row) -> {
                CatalogRow current = row == null
                        ? new CatalogRow(schema.code(), schema.documentType(), schema.country())
                        : row;
                if (!current.sides.contains(schema.side())) {
                    current.sides.add(schema.side());
                }
                return current;
            });
        }
        List<IdvPolicyResponse.Document> out = new ArrayList<>();
        for (CatalogRow row : grouped.values()) {
            row.sides.sort(IdvPolicyService::sideOrder);
            boolean on = enabled == null || enabled.contains(row.code);
            out.add(new IdvPolicyResponse.Document(row.code, row.documentType, row.country, List.copyOf(row.sides), on));
        }
        return List.copyOf(out);
    }

    private Set<String> activeCodes() {
        Set<String> codes = new LinkedHashSet<>();
        for (IdvPolicyResponse.Document document : catalog(null)) {
            codes.add(document.code());
        }
        return codes;
    }

    private Checked check(PublishIdvPolicyRequest request) {
        List<ErrorDetail> errors = new ArrayList<>();
        PublishIdvPolicyRequest.Profile profile = request == null ? null : request.profile();
        PublishIdvPolicyRequest.Flow flow = request == null ? null : request.flow();
        VerificationDecision expired = outcome(profile == null ? null : profile.expiredOutcome(), "expired_outcome", errors);
        VerificationDecision attempts =
                outcome(profile == null ? null : profile.attemptsOutcome(), "attempts_outcome", errors);
        double ocr = range(profile == null ? null : profile.ocrReviewAt(), "ocr_review_at", OCR_MIN, OCR_MAX, errors);
        double liveness =
                range(profile == null ? null : profile.livenessPassAt(), "liveness_pass_at", LIVENESS_MIN, LIVENESS_MAX, errors);
        double faceFail = range(
                profile == null ? null : profile.faceMatchFailAt(),
                "face_match_fail_at",
                FACE_FAIL_MIN,
                FACE_FAIL_MAX,
                errors);
        double facePass = range(
                profile == null ? null : profile.faceMatchPassAt(),
                "face_match_pass_at",
                FACE_PASS_MIN,
                FACE_PASS_MAX,
                errors);
        if (scale(faceFail).compareTo(scale(facePass)) >= 0) {
            errors.add(new ErrorDetail("face_match_fail_at", "order"));
        }
        int ttl = rangeInt(flow == null ? null : flow.linkTtlSeconds(), "link_ttl_seconds", TTL_MIN, TTL_MAX, errors);
        int captures = rangeInt(
                flow == null ? null : flow.maxCaptureAttempts(), "max_capture_attempts", ATTEMPTS_MIN, ATTEMPTS_MAX, errors);
        int livenessAttempts = rangeInt(
                flow == null ? null : flow.maxLivenessAttempts(),
                "max_liveness_attempts",
                ATTEMPTS_MIN,
                ATTEMPTS_MAX,
                errors);
        Set<String> codes = documentCodes(request == null ? null : request.documentCodes(), errors);
        if (!errors.isEmpty()) {
            throw ApiException.unprocessable("validation_error", "Policy validation failed", errors);
        }
        return new Checked(expired, attempts, ocr, liveness, faceFail, facePass, ttl, captures, livenessAttempts, codes);
    }

    private Set<String> documentCodes(List<String> requested, List<ErrorDetail> errors) {
        if (requested == null || requested.isEmpty()) {
            errors.add(new ErrorDetail("document_codes", "empty"));
            return Set.of();
        }
        Set<String> active = activeCodes();
        Set<String> codes = new LinkedHashSet<>();
        for (String raw : requested) {
            String code = raw == null ? "" : raw.trim();
            if (code.isEmpty() || !active.contains(code)) {
                errors.add(new ErrorDetail("document_codes", "unknown"));
                return Set.of();
            }
            codes.add(code);
        }
        if (codes.isEmpty()) {
            errors.add(new ErrorDetail("document_codes", "empty"));
        }
        return Set.copyOf(codes);
    }

    private static VerificationDecision outcome(String raw, String field, List<ErrorDetail> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(new ErrorDetail(field, "invalid"));
            return VerificationDecision.DECLINED;
        }
        try {
            VerificationDecision decision = VerificationDecision.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            if (decision != VerificationDecision.DECLINED && decision != VerificationDecision.REVIEW) {
                errors.add(new ErrorDetail(field, "invalid"));
                return VerificationDecision.DECLINED;
            }
            return decision;
        } catch (IllegalArgumentException ex) {
            errors.add(new ErrorDetail(field, "invalid"));
            return VerificationDecision.DECLINED;
        }
    }

    private static double range(Double value, String field, double min, double max, List<ErrorDetail> errors) {
        if (value == null || !Double.isFinite(value)) {
            errors.add(new ErrorDetail(field, "invalid"));
            return min;
        }
        BigDecimal scaled = scale(value);
        if (scaled.compareTo(scale(min)) < 0) {
            errors.add(new ErrorDetail(field, "below_minimum"));
        } else if (scaled.compareTo(scale(max)) > 0) {
            errors.add(new ErrorDetail(field, "above_maximum"));
        }
        return value;
    }

    private static int rangeInt(Integer value, String field, int min, int max, List<ErrorDetail> errors) {
        if (value == null) {
            errors.add(new ErrorDetail(field, "invalid"));
            return min;
        }
        if (value < min) {
            errors.add(new ErrorDetail(field, "below_minimum"));
        } else if (value > max) {
            errors.add(new ErrorDetail(field, "above_maximum"));
        }
        return value;
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }

    private static int sideOrder(String left, String right) {
        return Integer.compare(sideRank(left), sideRank(right));
    }

    private static int sideRank(String side) {
        if ("FRONT".equals(side)) {
            return 0;
        }
        if ("BACK".equals(side)) {
            return 1;
        }
        return 2;
    }

    private record CatalogRow(String code, String documentType, String country, List<String> sides) {
        private CatalogRow(String code, String documentType, String country) {
            this(code, documentType, country, new ArrayList<>());
        }
    }

    private record Checked(
            VerificationDecision expired,
            VerificationDecision attempts,
            double ocr,
            double liveness,
            double faceFail,
            double facePass,
            int ttl,
            int captures,
            int livenessAttempts,
            Set<String> codes) {}
}
