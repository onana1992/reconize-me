package com.kyc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.config.KycProperties;
import com.kyc.dto.idv.CompleteCaptureResponse;
import com.kyc.dto.idv.CompleteUploadRequest;
import com.kyc.dto.idv.ConsentRequest;
import com.kyc.dto.idv.ConsentResponse;
import com.kyc.dto.idv.FlowSessionResponse;
import com.kyc.dto.idv.UploadResponse;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.Consent;
import com.kyc.entities.Integration;
import com.kyc.entities.Verification;
import com.kyc.entities.VerificationMedia;
import com.kyc.entities.VerificationSignal;
import com.kyc.enums.ConsentDecision;
import com.kyc.enums.Enums;
import com.kyc.enums.MediaKind;
import com.kyc.enums.MediaStatus;
import com.kyc.enums.VerificationDecision;
import com.kyc.enums.VerificationStatus;
import com.kyc.adapters.VisionDocumentAi;
import com.kyc.dto.documentia.ParsedDocument;
import com.kyc.ports.BiometricAiPort;
import com.kyc.ports.DocumentAiPort;
import com.kyc.ports.HostedTokenStore;
import com.kyc.ports.ObjectStoragePort;
import com.kyc.ports.ProviderUnavailableException;
import com.kyc.services.documentia.DocumentLiveView;
import com.kyc.services.documentia.SchemaRegistry;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.ConsentRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.VerificationMediaRepository;
import com.kyc.repositories.VerificationRepository;
import com.kyc.repositories.VerificationSignalRepository;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HostedFlowService {

    private static final Logger log = LoggerFactory.getLogger(HostedFlowService.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration UPLOAD_TTL = Duration.ofMinutes(5);
    private static final Set<VerificationStatus> DOCUMENT_UPLOAD = EnumSet.of(
            VerificationStatus.PENDING_APPLICANT, VerificationStatus.DOCUMENT, VerificationStatus.RECAPTURE_REQUESTED);
    private static final Set<VerificationStatus> SELFIE_UPLOAD =
            EnumSet.of(VerificationStatus.SELFIE, VerificationStatus.RECAPTURE_REQUESTED);

    private final VerificationRepository verifications;
    private final ConsentRepository consents;
    private final VerificationMediaRepository media;
    private final VerificationSignalRepository signals;
    private final AuditEventRepository auditEvents;
    private final IntegrationRepository integrations;
    private final HostedTokenStore hostedTokens;
    private final ObjectStoragePort objectStorage;
    private final DocumentAiPort stubDocumentAi;
    private final VisionDocumentAi liveDocumentAi;
    private final SchemaRegistry schemas;
    private final BiometricAiPort stubBiometricAi;
    private final BiometricAiPort liveBiometricAi;
    private final IdvDecisionEngine engine;
    private final KycProperties properties;
    private final ObjectMapper objectMapper;
    private final WebhookService webhookService;
    private final DocumentAnalysisBuffer analysesBuffer;
    private final DocumentAnalysisStore analyses;

    public HostedFlowService(
            VerificationRepository verifications,
            ConsentRepository consents,
            VerificationMediaRepository media,
            VerificationSignalRepository signals,
            AuditEventRepository auditEvents,
            IntegrationRepository integrations,
            HostedTokenStore hostedTokens,
            ObjectStoragePort objectStorage,
            @Qualifier("stubDocumentAi") DocumentAiPort stubDocumentAi,
            @Qualifier("liveDocumentAi") VisionDocumentAi liveDocumentAi,
            SchemaRegistry schemas,
            @Qualifier("stubBiometricAi") BiometricAiPort stubBiometricAi,
            @Qualifier("liveBiometricAi") BiometricAiPort liveBiometricAi,
            KycProperties properties,
            ObjectMapper objectMapper,
            WebhookService webhookService,
            DocumentAnalysisBuffer analysesBuffer,
            DocumentAnalysisStore analyses) {
        this.verifications = verifications;
        this.consents = consents;
        this.media = media;
        this.signals = signals;
        this.auditEvents = auditEvents;
        this.integrations = integrations;
        this.hostedTokens = hostedTokens;
        this.objectStorage = objectStorage;
        this.stubDocumentAi = stubDocumentAi;
        this.liveDocumentAi = liveDocumentAi;
        this.schemas = schemas;
        this.stubBiometricAi = stubBiometricAi;
        this.liveBiometricAi = liveBiometricAi;
        this.engine = new IdvDecisionEngine();
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webhookService = webhookService;
        this.analysesBuffer = analysesBuffer;
        this.analyses = analyses;
    }

    @Transactional
    public FlowSessionResponse hydrate(String token) {
        Loaded loaded = load(token);
        Instant now = Instant.now();
        if (loaded.verification().getStatus() == VerificationStatus.CREATED) {
            loaded.verification().markOpened(now);
            audit(loaded, "hosted_link.opened", now);
        }
        return toFlow(loaded.verification());
    }

    @Transactional
    public ConsentResponse consent(String token, ConsentRequest request, String ip, String userAgent) {
        Loaded loaded = load(token);
        Verification verification = loaded.verification();
        ConsentDecision decision;
        try {
            decision = ConsentDecision.valueOf(
                    (request == null || request.decision() == null ? "" : request.decision())
                            .trim()
                            .toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw ApiException.validation(
                    "Invalid consent decision", List.of(new ErrorDetail("decision", "invalid")));
        }
        if (consents.existsByVerificationId(verification.getId())) {
            throw ApiException.conflict("consent_already_recorded", "Consent already recorded");
        }
        if (verification.getStatus() != VerificationStatus.PENDING_CONSENT
                && verification.getStatus() != VerificationStatus.CREATED) {
            throw ApiException.conflict("invalid_status", "Consent is not expected");
        }
        Instant now = Instant.now();
        if (verification.getStatus() == VerificationStatus.CREATED) {
            verification.markOpened(now);
        }
        String ua = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 512));
        String ipHash = ip == null || ip.isBlank() ? null : CryptoTokens.sha256HexPeppered(properties.ipHashPepper(), ip);
        consents.save(new Consent(
                UUID.randomUUID(),
                verification.getId(),
                decision,
                properties.consentTextVersion(),
                now,
                ipHash,
                ua));
        if (decision == ConsentDecision.ACCEPTED) {
            verification.markConsentAccepted(now);
            audit(loaded, "consent.accepted", now);
        } else {
            verification.markConsentDeclined(now);
            audit(loaded, "consent.declined", now);
        }
        return new ConsentResponse(Enums.json(verification.getStatus()), nextOf(verification), now);
    }

    @Transactional
    public UploadResponse documentUpload(String token) {
        Loaded loaded = load(token);
        requireConsent(loaded.verification());
        Verification verification = loaded.verification();
        if (!DOCUMENT_UPLOAD.contains(verification.getStatus())) {
            throw ApiException.conflict("invalid_status", "Document capture is not expected");
        }
        if (backTurn(verification)) {
            return issueUpload(loaded, MediaKind.DOCUMENT_BACK);
        }
        if (accepted(verification.getId(), MediaKind.DOCUMENT).isPresent()) {
            throw ApiException.conflict("invalid_status", "Document capture is not expected");
        }
        return issueUpload(loaded, MediaKind.DOCUMENT);
    }

    @Transactional
    public CompleteCaptureResponse documentComplete(String token, CompleteUploadRequest request) {
        Loaded loaded = load(token);
        requireConsent(loaded.verification());
        if (!DOCUMENT_UPLOAD.contains(loaded.verification().getStatus())) {
            throw ApiException.conflict("invalid_status", "Document capture is not expected");
        }
        boolean explicitBack = "back".equals(request == null ? null : request.side());
        MediaKind kind = explicitBack || backTurn(loaded.verification()) ? MediaKind.DOCUMENT_BACK : MediaKind.DOCUMENT;
        CompleteCaptureResponse response = completeMedia(
                loaded, kind, request == null ? null : request.attempt(), MediaQuality::acceptableDocument);
        if (!Boolean.TRUE.equals(response.accepted())) {
            return response;
        }
        Instant now = Instant.now();
        if (kind == MediaKind.DOCUMENT && askForBack(loaded, now)) {
            loaded.verification().requireDocumentBack(now);
            return new CompleteCaptureResponse(
                    Enums.json(loaded.verification().getStatus()),
                    nextOf(loaded.verification()),
                    true,
                    response.attempt(),
                    null);
        }
        if (kind == MediaKind.DOCUMENT_BACK && accepted(loaded.verification().getId(), MediaKind.DOCUMENT).isEmpty()) {
            return response;
        }
        if (kind == MediaKind.DOCUMENT || kind == MediaKind.DOCUMENT_BACK) {
            if (kind == MediaKind.DOCUMENT_BACK) {
                storeBack(loaded, now);
            }
            loaded.verification().markSelfie(now);
            return new CompleteCaptureResponse(
                    Enums.json(loaded.verification().getStatus()),
                    nextOf(loaded.verification()),
                    true,
                    response.attempt(),
                    null);
        }
        return response;
    }

    @Transactional
    public UploadResponse selfieUpload(String token) {
        Loaded loaded = load(token);
        requireConsent(loaded.verification());
        if (!SELFIE_UPLOAD.contains(loaded.verification().getStatus())
                || accepted(loaded.verification().getId(), MediaKind.DOCUMENT).isEmpty()
                || accepted(loaded.verification().getId(), MediaKind.SELFIE).isPresent()
                || backStillRequired(loaded.verification())) {
            throw ApiException.conflict("invalid_status", "Selfie capture is not expected");
        }
        return issueUpload(loaded, MediaKind.SELFIE);
    }

    @Transactional
    public CompleteCaptureResponse selfieComplete(String token, CompleteUploadRequest request) {
        Loaded loaded = load(token);
        requireConsent(loaded.verification());
        if (!SELFIE_UPLOAD.contains(loaded.verification().getStatus())
                || accepted(loaded.verification().getId(), MediaKind.DOCUMENT).isEmpty()
                || backStillRequired(loaded.verification())) {
            throw ApiException.conflict("invalid_status", "Selfie capture is not expected");
        }
        CompleteCaptureResponse response = completeMedia(
                loaded, MediaKind.SELFIE, request == null ? null : request.attempt(), MediaQuality::acceptableSelfie);
        if (!Boolean.TRUE.equals(response.accepted())) {
            return response;
        }
        Instant now = Instant.now();
        loaded.verification().markProcessing(now);
        decide(loaded, now);
        return new CompleteCaptureResponse(
                Enums.json(loaded.verification().getStatus()), nextOf(loaded.verification()), true, response.attempt(), null);
    }

    private void decide(Loaded loaded, Instant now) {
        Verification verification = loaded.verification();
        var document = accepted(verification.getId(), MediaKind.DOCUMENT)
                .orElseThrow(() -> ApiException.conflict("invalid_status", "Document is required"));
        var selfie = accepted(verification.getId(), MediaKind.SELFIE)
                .orElseThrow(() -> ApiException.conflict("invalid_status", "Selfie is required"));
        byte[] documentBytes = objectStorage.read(document.getObjectKey());
        byte[] selfieBytes = objectStorage.read(selfie.getObjectKey());
        Integration integration = integrations
                .findById(verification.getIntegrationId())
                .orElseThrow(() -> ApiException.notFound("Integration not found"));

        IdvDecisionEngine.Result result;
        String rulesVersion;
        if (integration.isLive()) {
            rulesVersion = IdvDecisionEngine.VISION_RULES_VERSION;
            result = decideLive(verification.getId(), documentBytes, selfieBytes, now);
        } else {
            rulesVersion = IdvDecisionEngine.RULES_VERSION;
            String scenario = verification.getSandboxScenario() == null ? "approved" : verification.getSandboxScenario();
            stubDocumentAi.analyze(documentBytes, scenario);
            stubBiometricAi.evaluate(documentBytes, selfieBytes, scenario);
            result = engine.decide(scenario);
        }

        String reasons;
        String extracted;
        try {
            reasons = objectMapper.writeValueAsString(result.reasons());
            extracted = result.extractedIdentity() == null || result.extractedIdentity().isEmpty()
                    ? null
                    : objectMapper.writeValueAsString(result.extractedIdentity());
        } catch (JsonProcessingException e) {
            reasons = "[]";
            extracted = null;
        }
        verification.decide(result.decision(), reasons, rulesVersion, extracted, now);
        for (IdvDecisionEngine.Signal signal : result.signals()) {
            signals.save(new VerificationSignal(
                    UUID.randomUUID(),
                    verification.getId(),
                    signal.code(),
                    signal.outcome(),
                    signal.score(),
                    now));
        }
        audit(loaded, "verification.completed", "{\"decision\":\"" + Enums.json(result.decision()) + "\"}", now);
        webhookService.enqueueCompleted(verification, now);
    }

    private IdvDecisionEngine.Result decideLive(UUID verificationId, byte[] documentBytes, byte[] selfieBytes, Instant now) {
        try {
            IdvDecisionEngine.Result document = replayOrAnalyze(verificationId, documentBytes, now);
            if (document.decision() != VerificationDecision.APPROVED) {
                return document;
            }
            Verification verification = verifications.findById(verificationId).orElse(null);
            if (verification != null && verification.isDocumentBackRequired()) {
                IdvDecisionEngine.Result back = analyses
                        .find(verificationId, "BACK")
                        .map(row -> analyses.replay(row, schemas, engine))
                        .orElseGet(() -> engine.review("provider_unavailable"));
                if (back.decision() != VerificationDecision.APPROVED) {
                    VerificationDecision decision = back.decision() == null
                            ? VerificationDecision.REVIEW
                            : back.decision();
                    return new IdvDecisionEngine.Result(
                            decision, back.reasons(), back.signals(), document.extractedIdentity());
                }
            }
            BiometricAiPort.BiometricSignals bioSignals =
                    liveBiometricAi.evaluate(documentBytes, selfieBytes, null);
            return engine.applyFace(document, bioSignals);
        } catch (ProviderUnavailableException e) {
            analysesBuffer.take();
            return engine.providerUnavailable();
        }
    }

    private IdvDecisionEngine.Result replayOrAnalyze(UUID verificationId, byte[] documentBytes, Instant now) {
        var stored = analyses.find(verificationId, "FRONT");
        if (stored.isPresent()) {
            return analyses.replay(stored.get(), schemas, engine);
        }
        liveDocumentAi.analyze(documentBytes, null);
        DocumentLiveView view = analysesBuffer.take();
        if (view == null) {
            return engine.providerUnavailable();
        }
        analyses.saveAccepted(verificationId, view, "FRONT", now);
        IdvDecisionEngine.Result result = view.result();
        if (result.decision() == null) {
            return new IdvDecisionEngine.Result(
                    VerificationDecision.REVIEW, result.reasons(), result.signals(), result.extractedIdentity());
        }
        return result;
    }

    private boolean askForBack(Loaded loaded, Instant now) {
        Integration integration = integrations
                .findById(loaded.verification().getIntegrationId())
                .orElse(null);
        if (integration == null || !integration.isLive()) {
            return false;
        }
        var front = accepted(loaded.verification().getId(), MediaKind.DOCUMENT).orElse(null);
        if (front == null) {
            return false;
        }
        try {
            DocumentLiveView view = liveDocumentAi.analyzeCapture(objectStorage.read(front.getObjectKey()), "FRONT");
            analyses.saveAccepted(loaded.verification().getId(), view, "FRONT", now);
            if (!view.accepted() || view.result().decision() != VerificationDecision.APPROVED) {
                return false;
            }
            String code = view.response().parsed() instanceof ParsedDocument parsed && parsed.classification() != null
                    ? parsed.classification().code()
                    : null;
            return schemas.hasActiveSide(code, "BACK");
        } catch (ProviderUnavailableException e) {
            log.info("document-ia capture side=FRONT code=provider_unavailable");
            return false;
        }
    }

    private void storeBack(Loaded loaded, Instant now) {
        Integration integration = integrations
                .findById(loaded.verification().getIntegrationId())
                .orElse(null);
        if (integration == null || !integration.isLive()) {
            return;
        }
        var back = accepted(loaded.verification().getId(), MediaKind.DOCUMENT_BACK).orElse(null);
        if (back == null) {
            return;
        }
        try {
            DocumentLiveView view = liveDocumentAi.analyzeCapture(objectStorage.read(back.getObjectKey()), "BACK");
            analyses.saveAccepted(loaded.verification().getId(), view, "BACK", now);
        } catch (ProviderUnavailableException e) {
            log.info("document-ia capture side=BACK code=provider_unavailable");
        }
    }

    private boolean backTurn(Verification verification) {
        return verification.isDocumentBackRequired()
                && accepted(verification.getId(), MediaKind.DOCUMENT).isPresent()
                && accepted(verification.getId(), MediaKind.DOCUMENT_BACK).isEmpty();
    }

    private boolean backStillRequired(Verification verification) {
        return verification.isDocumentBackRequired()
                && accepted(verification.getId(), MediaKind.DOCUMENT_BACK).isEmpty();
    }

    private UploadResponse issueUpload(Loaded loaded, MediaKind kind) {
        long used = media.countByVerificationIdAndKind(loaded.verification().getId(), kind);
        if (used >= MAX_ATTEMPTS) {
            throw ApiException.conflict("invalid_status", "Capture attempts exceeded");
        }
        int attempt = (int) used + 1;
        Instant now = Instant.now();
        if (kind == MediaKind.DOCUMENT && loaded.verification().getStatus() == VerificationStatus.PENDING_APPLICANT) {
            loaded.verification().markDocument(now);
        }
        String objectKey = "org/"
                + loaded.organizationId()
                + "/verifications/"
                + loaded.verification().getId()
                + "/"
                + Enums.json(kind)
                + "/"
                + attempt;
        VerificationMedia item = new VerificationMedia(
                UUID.randomUUID(), loaded.verification().getId(), kind, attempt, objectKey, now);
        media.save(item);
        ObjectStoragePort.SignedUrl signed;
        try {
            signed = objectStorage.createSignedUploadUrl(objectKey, "image/jpeg", UPLOAD_TTL);
        } catch (RuntimeException e) {
            throw ApiException.dependencyUnavailable("Object storage unavailable");
        }
        return new UploadResponse(signed.url(), objectKey, signed.expiresAt(), attempt);
    }

    private CompleteCaptureResponse completeMedia(
            Loaded loaded, MediaKind kind, Integer attempt, java.util.function.Predicate<byte[]> quality) {
        if (attempt == null || attempt < 1) {
            throw ApiException.validation("Invalid attempt", List.of(new ErrorDetail("attempt", "invalid")));
        }
        VerificationMedia item = media.findByVerificationIdAndKindAndAttempt(
                        loaded.verification().getId(), kind, attempt)
                .orElseThrow(() -> ApiException.validation("Unknown attempt", List.of(new ErrorDetail("attempt", "unknown"))));
        if (item.getStatus() != MediaStatus.PENDING) {
            throw ApiException.conflict("invalid_status", "Attempt already completed");
        }
        if (!objectStorage.exists(item.getObjectKey())) {
            throw ApiException.validation("Object not uploaded", List.of(new ErrorDetail("object", "missing")));
        }
        byte[] body = objectStorage.read(item.getObjectKey());
        Instant now = Instant.now();
        if (!quality.test(body)) {
            item.rejectQuality();
            discardRejectedObject(item.getObjectKey());
            if (media.countByVerificationIdAndKind(loaded.verification().getId(), kind) >= MAX_ATTEMPTS) {
                loaded.verification().declineCaptureAttempts(now);
                hostedTokens.revokeByVerificationId(loaded.verification().getId());
                audit(loaded, "verification.declined", "{\"reason\":\"capture_attempts_exceeded\"}", now);
                return new CompleteCaptureResponse(Enums.json(loaded.verification().getStatus()), "done", false, attempt, true);
            }
            loaded.verification().markRecapture(now);
            return new CompleteCaptureResponse(
                    Enums.json(loaded.verification().getStatus()), nextOf(loaded.verification()), false, attempt, true);
        }
        item.accept(MediaQuality.sniff(body), body.length);
        return new CompleteCaptureResponse(
                Enums.json(loaded.verification().getStatus()), nextOf(loaded.verification()), true, attempt, false);
    }

    private void discardRejectedObject(String objectKey) {
        try {
            objectStorage.delete(objectKey);
        } catch (RuntimeException e) {
            log.warn("Failed to delete rejected capture key={}", objectKey, e);
        }
    }

    private Loaded load(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.notFound("Verification not found");
        }
        Instant now = Instant.now();
        var entry = hostedTokens.get(token);
        if (entry.isPresent()) {
            Verification verification = verifications
                    .findByIdAndOrganizationId(entry.get().verificationId(), entry.get().organizationId())
                    .orElseThrow(() -> ApiException.notFound("Verification not found"));
            if (verification.expired(now) || verification.getStatus() == VerificationStatus.CANCELLED) {
                expireIfNeeded(verification, now, entry.get().organizationId());
                throw ApiException.gone("hosted_link_expired", "This link has expired");
            }
            return new Loaded(entry.get().organizationId(), verification);
        }
        Verification stale = verifications
                .findByHostedTokenHash(CryptoTokens.sha256Hex(token))
                .orElseThrow(() -> ApiException.notFound("Verification not found"));
        expireIfNeeded(stale, now, stale.getOrganizationId());
        throw ApiException.gone("hosted_link_expired", "This link has expired");
    }

    private void expireIfNeeded(Verification verification, Instant now, UUID organizationId) {
        if (verification.terminal()) {
            return;
        }
        verification.expire(now);
        hostedTokens.revokeByVerificationId(verification.getId());
        auditEvents.save(new AuditEvent(
                organizationId, "applicant", null, "verification.expired", "verification", verification.getId(), "{}", now));
    }

    private void requireConsent(Verification verification) {
        Consent consent = consents
                .findByVerificationId(verification.getId())
                .orElseThrow(() -> ApiException.conflict("invalid_status", "Consent is required"));
        if (consent.getDecision() != ConsentDecision.ACCEPTED) {
            throw ApiException.conflict("invalid_status", "Consent was declined");
        }
    }

    private java.util.Optional<VerificationMedia> accepted(UUID verificationId, MediaKind kind) {
        return media.findFirstByVerificationIdAndKindAndStatusOrderByAttemptDesc(
                verificationId, kind, MediaStatus.ACCEPTED);
    }

    private FlowSessionResponse toFlow(Verification verification) {
        return new FlowSessionResponse(
                verification.getId(),
                Enums.json(verification.getStatus()),
                properties.consentTextVersion(),
                verification.getHostedExpiresAt(),
                nextOf(verification));
    }

    private String nextOf(Verification verification) {
        return switch (verification.getStatus()) {
            case CREATED, PENDING_CONSENT -> "consent";
            case PENDING_APPLICANT, DOCUMENT, RECAPTURE_REQUESTED -> {
                if (accepted(verification.getId(), MediaKind.DOCUMENT).isEmpty()) {
                    yield "capture_document";
                }
                if (backTurn(verification)) {
                    yield "capture_document_back";
                }
                yield "capture_selfie";
            }
            case SELFIE -> "capture_selfie";
            case PROCESSING -> "wait";
            default -> "done";
        };
    }

    private void audit(Loaded loaded, String action, Instant now) {
        audit(loaded, action, "{}", now);
    }

    private void audit(Loaded loaded, String action, String payload, Instant now) {
        auditEvents.save(new AuditEvent(
                loaded.organizationId(),
                "applicant",
                null,
                action,
                "verification",
                loaded.verification().getId(),
                payload,
                now));
    }

    private record Loaded(UUID organizationId, Verification verification) {}
}
