package com.kyc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.adapters.InMemoryFaceCollectionClient;
import com.kyc.config.KycProperties;
import com.kyc.dto.idv.CompleteCaptureResponse;
import com.kyc.dto.idv.ConsentRequest;
import com.kyc.dto.idv.ConsentResponse;
import com.kyc.dto.idv.FlowSessionResponse;
import com.kyc.dto.idv.LivenessCompleteRequest;
import com.kyc.dto.idv.LivenessSessionResponse;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.AuthenticationLivenessCheck;
import com.kyc.entities.AuthenticationMedia;
import com.kyc.entities.AuthenticationSession;
import com.kyc.entities.Enrollment;
import com.kyc.enums.AuthenticationStatus;
import com.kyc.enums.ConsentDecision;
import com.kyc.enums.EnrollmentStatus;
import com.kyc.enums.Enums;
import com.kyc.enums.LivenessCheckStatus;
import com.kyc.enums.MediaKind;
import com.kyc.ports.FaceCollectionClient;
import com.kyc.ports.FaceLivenessClient;
import com.kyc.ports.FaceLivenessClient.LivenessResult;
import com.kyc.ports.HostedTokenStore;
import com.kyc.ports.LivenessCredentialPort;
import com.kyc.ports.ObjectStoragePort;
import com.kyc.ports.ProviderUnavailableException;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.AuthenticationLivenessCheckRepository;
import com.kyc.repositories.AuthenticationMediaRepository;
import com.kyc.repositories.AuthenticationRepository;
import com.kyc.repositories.EnrollmentRepository;
import com.kyc.services.auth.AuthDecisionEngine;
import com.kyc.services.auth.AuthPolicy;
import com.kyc.services.auth.AuthPolicyService;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hosted auth-2 flow: consent → Face Liveness → SearchFacesByImage → decision in the closure body.
 * No webhook, no CompareFaces, no sandbox branch.
 */
@Service
public class AuthenticationHostedFlowService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationHostedFlowService.class);
    private static final Duration LIVENESS_TTL = Duration.ofMinutes(3);
    private static final String PRODUCT = "authentication";

    private final AuthenticationRepository authentications;
    private final AuthenticationLivenessCheckRepository livenessChecks;
    private final AuthenticationMediaRepository media;
    private final EnrollmentRepository enrollments;
    private final AuditEventRepository auditEvents;
    private final HostedTokenStore hostedTokens;
    private final FaceLivenessClient faceLiveness;
    private final LivenessCredentialPort livenessCredentials;
    private final FaceCollectionClient faceCollection;
    private final ObjectStoragePort objectStorage;
    private final KycProperties properties;
    private final ObjectMapper objectMapper;
    private final AuthPolicyService policies;
    private final CreditService credits;
    private final AuthenticationCaptureStore captures;
    private final AuthDecisionEngine engine = new AuthDecisionEngine();

    public AuthenticationHostedFlowService(
            AuthenticationRepository authentications,
            AuthenticationLivenessCheckRepository livenessChecks,
            AuthenticationMediaRepository media,
            EnrollmentRepository enrollments,
            AuditEventRepository auditEvents,
            HostedTokenStore hostedTokens,
            FaceLivenessClient faceLiveness,
            LivenessCredentialPort livenessCredentials,
            FaceCollectionClient faceCollection,
            ObjectStoragePort objectStorage,
            KycProperties properties,
            ObjectMapper objectMapper,
            AuthPolicyService policies,
            CreditService credits,
            AuthenticationCaptureStore captures) {
        this.authentications = authentications;
        this.livenessChecks = livenessChecks;
        this.media = media;
        this.enrollments = enrollments;
        this.auditEvents = auditEvents;
        this.hostedTokens = hostedTokens;
        this.faceLiveness = faceLiveness;
        this.livenessCredentials = livenessCredentials;
        this.faceCollection = faceCollection;
        this.objectStorage = objectStorage;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.policies = policies;
        this.credits = credits;
        this.captures = captures;
    }

    public boolean owns(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        var entry = hostedTokens.get(token);
        if (entry.isPresent()) {
            return entry.get().authentication();
        }
        return authentications.findByHostedTokenHash(CryptoTokens.sha256Hex(token)).isPresent();
    }

    @Transactional
    public FlowSessionResponse hydrate(String token) {
        Loaded loaded = load(token);
        Instant now = Instant.now();
        if (loaded.session().getStatus() == AuthenticationStatus.CREATED) {
            loaded.session().markOpened(now);
            audit(loaded, "hosted_link.opened", now);
        }
        return toFlow(loaded.session());
    }

    @Transactional
    public ConsentResponse consent(String token, ConsentRequest request) {
        Loaded loaded = load(token);
        AuthenticationSession session = loaded.session();
        ConsentDecision decision = parseConsent(request);
        Instant now = Instant.now();
        if (session.getStatus() == AuthenticationStatus.CREATED) {
            session.markOpened(now);
        }
        if (session.getConsentAt() != null) {
            ConsentDecision prior =
                    session.terminal() && primaryReason(session).equals("consent_refused")
                            ? ConsentDecision.DECLINED
                            : ConsentDecision.ACCEPTED;
            if (prior != decision) {
                throw ApiException.conflict("invalid_status", "Consent already recorded with a different decision");
            }
            return new ConsentResponse(Enums.json(session.getStatus()), nextOf(session), session.getConsentAt());
        }
        if (session.getStatus() != AuthenticationStatus.PENDING_CONSENT) {
            throw ApiException.conflict("invalid_status", "Consent is not expected");
        }
        String version = properties.authenticationConsentTextVersion();
        if (decision == ConsentDecision.ACCEPTED) {
            session.acceptConsent(version, now);
            audit(loaded, "consent.accepted", now);
            return new ConsentResponse(Enums.json(session.getStatus()), nextOf(session), now);
        }
        session.refuseConsent(version, reasonsJson(List.of("consent_refused")), now);
        hostedTokens.revokeByVerificationId(session.getId());
        audit(loaded, "consent.declined", now);
        finalizeDecision(session);
        discardSelfies(session);
        return new ConsentResponse(Enums.json(session.getStatus()), nextOf(session), now);
    }

    @Transactional
    public LivenessSessionResponse openLiveness(String token, boolean fresh) {
        Loaded loaded = load(token);
        requireConsentAccepted(loaded.session());
        requireFaceStep(loaded.session());
        AuthPolicy policy = policies.frozen(loaded.session().getIntegrationId(), loaded.session().getPolicyVersion());
        if (completedAttempts(loaded.session()) >= policy.maxCaptureAttempts()) {
            declineAttemptLimit(loaded);
            throw ApiException.conflict("liveness_attempts_exceeded", "Liveness attempts exceeded");
        }
        Instant now = Instant.now();
        var open = livenessChecks.findFirstByAuthenticationIdAndStatusOrderByAttemptDesc(
                loaded.session().getId(), LivenessCheckStatus.OPEN);
        if (open.isPresent()) {
            AuthenticationLivenessCheck check = open.get();
            if (!fresh && check.getExpiresAt().isAfter(now)) {
                return credentialsFor(check);
            }
            check.markExpired();
            livenessChecks.saveAndFlush(check);
        }
        int attempt = (int) livenessChecks.countByAuthenticationId(loaded.session().getId()) + 1;
        String requestToken = loaded.session().getId().toString().replace("-", "") + attempt;
        String sessionId;
        try {
            sessionId = faceLiveness.createSession(requestToken);
        } catch (ProviderUnavailableException e) {
            throw ApiException.dependencyUnavailable("Face Liveness unavailable");
        }
        AuthenticationLivenessCheck created = new AuthenticationLivenessCheck(
                UUID.randomUUID(),
                loaded.session().getId(),
                attempt,
                sessionId,
                properties.aws().region(),
                now.plus(LIVENESS_TTL),
                now);
        try {
            livenessChecks.saveAndFlush(created);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("liveness_session_busy", "A liveness session is already being opened");
        }
        log.info("Auth Face Liveness session opened authenticationId={} attempt={}", loaded.session().getId(), attempt);
        return credentialsFor(created);
    }

    @Transactional
    public CompleteCaptureResponse completeLiveness(String token, LivenessCompleteRequest request) {
        Loaded loaded = load(token);
        AuthenticationSession session = loaded.session();
        requireConsentAccepted(session);
        requireFaceStep(session);
        AuthPolicy policy = policies.frozen(loaded.session().getIntegrationId(), loaded.session().getPolicyVersion());
        if (completedAttempts(session) >= policy.maxCaptureAttempts()) {
            declineAttemptLimit(loaded);
            return closed(session, null);
        }
        AuthenticationLivenessCheck check = livenessChecks
                .findFirstByAuthenticationIdAndStatusOrderByAttemptDesc(session.getId(), LivenessCheckStatus.OPEN)
                .orElseThrow(() -> ApiException.conflict("invalid_status", "Liveness session is not open"));
        String presented = request == null ? null : request.sessionId();
        if (presented != null && !presented.isBlank() && !presented.equals(check.getSessionId())) {
            throw ApiException.conflict("liveness_session_mismatch", "Liveness session does not match");
        }
        Instant now = Instant.now();
        if (!check.getExpiresAt().isAfter(now)) {
            check.markExpired();
            throw ApiException.conflict("liveness_expired", "Liveness session expired");
        }
        long start = System.nanoTime();
        LivenessResult result;
        try {
            result = faceLiveness.getResults(check.getSessionId());
        } catch (ProviderUnavailableException e) {
            return failProvider(loaded, "provider_unavailable", now, check.getAttempt());
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        String status = result == null ? "missing" : result.status();
        Double confidence = result == null ? null : result.confidence();
        log.info(
                "Auth Face Liveness completed authenticationId={} durationMs={} status={} confidence={} attempt={}",
                session.getId(),
                durationMs,
                status,
                confidence,
                check.getAttempt());
        if (result == null || "EXPIRED".equalsIgnoreCase(result.status())) {
            check.markExpired();
            throw ApiException.conflict("liveness_expired", "Liveness session expired");
        }
        check.complete(confidence == null ? 0.0 : confidence);

        if (isPresentationAttack(result)) {
            storeReference(loaded, result.referenceImage(), check.getAttempt(), now);
            return decide(
                    loaded,
                    engine.decide(true, session.getFaceId(), true, false, List.of(), null, policy),
                    now,
                    check.getAttempt());
        }

        boolean livenessPass = isLivenessPass(result);
        byte[] reference = result.referenceImage() == null ? new byte[0] : result.referenceImage();
        if (!livenessPass || reference.length == 0) {
            storeReference(loaded, reference, check.getAttempt(), now);
            if (completedAttempts(session) >= policy.maxCaptureAttempts()) {
                return decide(
                        loaded,
                        AuthDecisionEngine.Result.declined("attempt_limit"),
                        now,
                        check.getAttempt());
            }
            session.requestRecapture();
            authentications.save(session);
            return new CompleteCaptureResponse(
                    Enums.json(session.getStatus()),
                    "liveness",
                    false,
                    check.getAttempt(),
                    true,
                    "liveness_fail");
        }

        storeReference(loaded, reference, check.getAttempt(), now);

        Enrollment enrollment = enrollments
                .findByIdAndOrganizationId(session.getEnrollmentId(), session.getOrganizationId())
                .orElse(null);
        boolean enrollmentActive =
                enrollment != null && enrollment.getStatus() == EnrollmentStatus.ACTIVE;
        if (!enrollmentActive) {
            return decide(
                    loaded,
                    engine.decide(false, session.getFaceId(), false, true, List.of(), null, policy),
                    now,
                    check.getAttempt());
        }

        float thresholdPoints = policy.reviewThreshold().multiply(java.math.BigDecimal.valueOf(100)).floatValue();
        FaceCollectionClient.SearchResult search;
        try {
            search = faceCollection.searchFacesByImage(enrollment.getCollectionId(), reference, thresholdPoints);
        } catch (InMemoryFaceCollectionClient.FaceCollectionException e) {
            return failProvider(loaded, e.reason(), now, check.getAttempt());
        } catch (RuntimeException e) {
            return failProvider(loaded, "provider_unavailable", now, check.getAttempt());
        }
        if (search instanceof FaceCollectionClient.SearchResult.Failed failed) {
            return failProvider(loaded, failed.reason(), now, check.getAttempt());
        }
        List<AuthDecisionEngine.Candidate> candidates =
                ((FaceCollectionClient.SearchResult.Ok) search)
                        .matches().stream()
                                .map(match -> new AuthDecisionEngine.Candidate(match.faceId(), match.similarity()))
                                .toList();
        return decide(
                loaded,
                engine.decide(true, session.getFaceId(), false, true, candidates, null, policy),
                now,
                check.getAttempt());
    }

    /** Document / selfie upload paths are identity-only. */
    public void rejectIdentityCapture() {
        throw ApiException.conflict("invalid_status", "Document capture is not expected");
    }

    private CompleteCaptureResponse decide(
            Loaded loaded, AuthDecisionEngine.Result result, Instant now, Integer attempt) {
        AuthenticationSession session = loaded.session();
        session.markProcessing();
        session.applyDecision(
                result.status(),
                reasonsJson(result.reasons()),
                result.matchScore(),
                result.failureReason(),
                now);
        hostedTokens.revokeByVerificationId(session.getId());
        authentications.save(session);
        audit(
                loaded,
                result.status() == AuthenticationStatus.FAILED ? "authentication.failed" : "authentication.decided",
                now);
        finalizeDecision(session);
        discardSelfies(session);
        return closed(session, attempt);
    }

    private CompleteCaptureResponse failProvider(Loaded loaded, String reason, Instant now, Integer attempt) {
        return decide(loaded, AuthDecisionEngine.Result.failed(reason), now, attempt);
    }

    private void declineAttemptLimit(Loaded loaded) {
        Instant now = Instant.now();
        decide(loaded, AuthDecisionEngine.Result.declined("attempt_limit"), now, null);
    }

    /**
     * One AUTHENTICATION unit ($0.10) at the end of an executed live session.
     * Not at creation. Analyst review, recapture and idempotent replay do not call this again.
     * {@link CreditService#debitForAuthentication} is itself idempotent.
     */
    private void finalizeDecision(AuthenticationSession session) {
        if (!billable(session)) {
            return;
        }
        credits.debitForAuthentication(session.getOrganizationId(), session.getId(), null, "system");
    }

    /** Closed sessions do not keep the captured selfie. Review still needs it until the analyst decides. */
    private void discardSelfies(AuthenticationSession session) {
        if (session.terminal()) {
            captures.discardSessionSelfies(session.getId());
        }
    }

    private static boolean billable(AuthenticationSession session) {
        AuthenticationStatus status = session.getStatus();
        if (status != AuthenticationStatus.APPROVED
                && status != AuthenticationStatus.REVIEW
                && status != AuthenticationStatus.DECLINED) {
            return false;
        }
        String reasons = session.getReasons();
        if (reasons != null
                && (reasons.contains("consent_refused") || reasons.contains("enrollment_inactive"))) {
            return false;
        }
        return true;
    }

    private CompleteCaptureResponse closed(AuthenticationSession session, Integer attempt) {
        return new CompleteCaptureResponse(
                Enums.json(session.getStatus()),
                nextOf(session),
                true,
                attempt,
                null,
                primaryReason(session),
                readReasons(session.getReasons()),
                session.getMatchScore(),
                session.getFailureReason());
    }

    private void storeReference(Loaded loaded, byte[] reference, int attempt, Instant now) {
        if (reference == null || reference.length == 0) {
            return;
        }
        String type = MediaQuality.sniff(reference);
        if (type == null) {
            type = "image/jpeg";
        }
        String objectKey = "kyc/org/"
                + loaded.organizationId()
                + "/authentications/"
                + loaded.session().getId()
                + "/selfie/"
                + attempt;
        objectStorage.write(objectKey, reference, type);
        AuthenticationMedia item = new AuthenticationMedia(
                UUID.randomUUID(), loaded.session().getId(), MediaKind.SELFIE, attempt, objectKey, now);
        item.accept(type, reference.length);
        media.save(item);
    }

    private LivenessSessionResponse credentialsFor(AuthenticationLivenessCheck check) {
        try {
            LivenessCredentialPort.Issued issued = livenessCredentials.issue();
            return new LivenessSessionResponse(
                    check.getSessionId(),
                    check.getRegion(),
                    new LivenessSessionResponse.Credentials(
                            issued.accessKeyId(),
                            issued.secretAccessKey(),
                            issued.sessionToken(),
                            issued.expiration()));
        } catch (ProviderUnavailableException e) {
            throw ApiException.dependencyUnavailable("Face Liveness unavailable");
        }
    }

    private Loaded load(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.notFound("Authentication not found");
        }
        Instant now = Instant.now();
        var entry = hostedTokens.get(token);
        if (entry.isPresent()) {
            if (!entry.get().authentication()) {
                throw ApiException.notFound("Authentication not found");
            }
            AuthenticationSession session = authentications
                    .findByIdAndOrganizationId(entry.get().verificationId(), entry.get().organizationId())
                    .orElseThrow(() -> ApiException.notFound("Authentication not found"));
            if (applicantClosed(session)) {
                return new Loaded(entry.get().organizationId(), session);
            }
            if (session.expired(now) || session.getStatus() == AuthenticationStatus.CANCELLED) {
                expireIfNeeded(session, now);
                throw ApiException.gone("hosted_link_expired", "This link has expired");
            }
            return new Loaded(entry.get().organizationId(), session);
        }
        AuthenticationSession stale = authentications
                .findByHostedTokenHash(CryptoTokens.sha256Hex(token))
                .orElseThrow(() -> ApiException.notFound("Authentication not found"));
        if (applicantClosed(stale)) {
            return new Loaded(stale.getOrganizationId(), stale);
        }
        expireIfNeeded(stale, now);
        throw ApiException.gone("hosted_link_expired", "This link has expired");
    }

    /** Decision already shown to the applicant. A later reload must not look like an expired link. */
    private static boolean applicantClosed(AuthenticationSession session) {
        AuthenticationStatus status = session.getStatus();
        return status == AuthenticationStatus.APPROVED
                || status == AuthenticationStatus.DECLINED
                || status == AuthenticationStatus.REVIEW
                || status == AuthenticationStatus.FAILED;
    }

    private void expireIfNeeded(AuthenticationSession session, Instant now) {
        if (session.terminal()) {
            return;
        }
        session.expire(now);
        hostedTokens.revokeByVerificationId(session.getId());
        authentications.save(session);
        discardSelfies(session);
        auditEvents.save(new AuditEvent(
                session.getOrganizationId(),
                "applicant",
                null,
                "authentication.expired",
                "authentication",
                session.getId(),
                "{}",
                now));
    }

    private void requireConsentAccepted(AuthenticationSession session) {
        if (session.getConsentAt() == null || session.getStatus() == AuthenticationStatus.PENDING_CONSENT) {
            throw ApiException.conflict("invalid_status", "Consent is required");
        }
        if (session.terminal() && "consent_refused".equals(primaryReason(session))) {
            throw ApiException.conflict("invalid_status", "Consent was declined");
        }
    }

    private void requireFaceStep(AuthenticationSession session) {
        if (session.getStatus() != AuthenticationStatus.SELFIE
                && session.getStatus() != AuthenticationStatus.RECAPTURE_REQUESTED) {
            throw ApiException.conflict("invalid_status", "Liveness is not expected");
        }
    }

    private long completedAttempts(AuthenticationSession session) {
        return livenessChecks.countByAuthenticationIdAndStatus(session.getId(), LivenessCheckStatus.COMPLETED);
    }

    private static boolean isPresentationAttack(LivenessResult result) {
        return result != null && "FAILED".equalsIgnoreCase(result.status());
    }

    private static boolean isLivenessPass(LivenessResult result) {
        if (result == null || !"SUCCEEDED".equalsIgnoreCase(result.status())) {
            return false;
        }
        return result.confidence() >= AuthPolicy.LIVENESS_PASS.doubleValue();
    }

    private FlowSessionResponse toFlow(AuthenticationSession session) {
        return new FlowSessionResponse(
                session.getId(),
                Enums.json(session.getStatus()),
                properties.authenticationConsentTextVersion(),
                session.getExpiresAt(),
                nextOf(session),
                primaryReason(session),
                PRODUCT);
    }

    private String nextOf(AuthenticationSession session) {
        return switch (session.getStatus()) {
            case CREATED, PENDING_CONSENT -> "consent";
            case SELFIE, RECAPTURE_REQUESTED -> "liveness";
            case PROCESSING -> "wait";
            case APPROVED, DECLINED, REVIEW, FAILED, EXPIRED, CANCELLED -> "done";
        };
    }

    private void audit(Loaded loaded, String action, Instant now) {
        auditEvents.save(new AuditEvent(
                loaded.organizationId(),
                "applicant",
                null,
                action,
                "authentication",
                loaded.session().getId(),
                "{}",
                now));
    }

    private String reasonsJson(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(reasons);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> readReasons(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String primaryReason(AuthenticationSession session) {
        List<String> reasons = readReasons(session.getReasons());
        if (reasons != null && !reasons.isEmpty()) {
            return reasons.get(0);
        }
        return session.getFailureReason();
    }

    private static ConsentDecision parseConsent(ConsentRequest request) {
        try {
            return ConsentDecision.valueOf(
                    (request == null || request.decision() == null ? "" : request.decision())
                            .trim()
                            .toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw ApiException.validation(
                    "Invalid consent decision", List.of(new ErrorDetail("decision", "invalid")));
        }
    }

    private record Loaded(UUID organizationId, AuthenticationSession session) {}
}
