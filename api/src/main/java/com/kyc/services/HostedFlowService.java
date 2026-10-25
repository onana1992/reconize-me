package com.kyc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.config.KycProperties;
import com.kyc.dto.idv.CompleteCaptureResponse;
import com.kyc.dto.idv.CompleteUploadRequest;
import com.kyc.dto.idv.ConsentRequest;
import com.kyc.dto.idv.ConsentResponse;
import com.kyc.dto.idv.FlowSessionResponse;
import com.kyc.dto.idv.LivenessCompleteRequest;
import com.kyc.dto.idv.LivenessSessionResponse;
import com.kyc.dto.idv.UploadResponse;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.Consent;
import com.kyc.entities.Integration;
import com.kyc.entities.Verification;
import com.kyc.entities.VerificationMedia;
import com.kyc.entities.VerificationLivenessCheck;
import com.kyc.entities.VerificationSignal;
import com.kyc.enums.ConsentDecision;
import com.kyc.enums.Enums;
import com.kyc.enums.LivenessCheckStatus;
import com.kyc.enums.MediaKind;
import com.kyc.enums.MediaStatus;
import com.kyc.enums.SignalOutcome;
import com.kyc.enums.VerificationDecision;
import com.kyc.enums.VerificationStatus;
import com.kyc.adapters.AwsBiometricAi;
import com.kyc.adapters.VisionDocumentAi;
import com.kyc.dto.documentia.DocumentDecision;
import com.kyc.dto.documentia.DocumentImageQuality;
import com.kyc.ports.BiometricAiPort;
import com.kyc.ports.DocumentAiPort;
import com.kyc.ports.FaceLivenessClient;
import com.kyc.ports.FaceLivenessClient.LivenessResult;
import com.kyc.ports.LivenessCredentialPort;
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
import com.kyc.repositories.VerificationLivenessCheckRepository;
import com.kyc.repositories.VerificationSignalRepository;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HostedFlowService {

    private static final Logger log = LoggerFactory.getLogger(HostedFlowService.class);
    private static final Duration UPLOAD_TTL = Duration.ofMinutes(5);
    private static final Duration LIVENESS_TTL = Duration.ofMinutes(3);
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
    private final AwsBiometricAi liveBiometricAi;
    private final FaceLivenessClient faceLiveness;
    private final LivenessCredentialPort livenessCredentials;
    private final VerificationLivenessCheckRepository livenessChecks;
    private final IdvDecisionEngine engine;
    private final KycProperties properties;
    private final ObjectMapper objectMapper;
    private final WebhookService webhookService;
    private final DocumentAnalysisBuffer analysesBuffer;
    private final DocumentAnalysisStore analyses;
    private final IdvPolicyService policies;
    private final com.kyc.services.aml.AmlAttachmentService amlAttachment;

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
            @Qualifier("liveBiometricAi") AwsBiometricAi liveBiometricAi,
            FaceLivenessClient faceLiveness,
            LivenessCredentialPort livenessCredentials,
            VerificationLivenessCheckRepository livenessChecks,
            KycProperties properties,
            ObjectMapper objectMapper,
            WebhookService webhookService,
            DocumentAnalysisBuffer analysesBuffer,
            DocumentAnalysisStore analyses,
            IdvPolicyService policies,
            com.kyc.services.aml.AmlAttachmentService amlAttachment) {
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
        this.faceLiveness = faceLiveness;
        this.livenessCredentials = livenessCredentials;
        this.livenessChecks = livenessChecks;
        this.engine = new IdvDecisionEngine();
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webhookService = webhookService;
        this.analysesBuffer = analysesBuffer;
        this.analyses = analyses;
        this.policies = policies;
        this.amlAttachment = amlAttachment;
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
        if (kind == MediaKind.DOCUMENT || kind == MediaKind.DOCUMENT_BACK) {
            CompleteCaptureResponse reread = rereadCapture(loaded, kind, response.attempt(), now);
            if (reread != null) {
                return reread;
            }
        }
        if (kind == MediaKind.DOCUMENT && askForBack(loaded)) {
            loaded.verification().requireDocumentBack(now);
            return captured(loaded, response.attempt());
        }
        if (kind == MediaKind.DOCUMENT_BACK && accepted(loaded.verification().getId(), MediaKind.DOCUMENT).isEmpty()) {
            return response;
        }
        if (kind == MediaKind.DOCUMENT || kind == MediaKind.DOCUMENT_BACK) {
            if (kind == MediaKind.DOCUMENT_BACK) {
                CompleteCaptureResponse mismatch = retryMismatch(loaded, response.attempt(), now);
                if (mismatch != null) {
                    return mismatch;
                }
            }
            if (finishDocumentBeforeFace(loaded, now)) {
                return captured(loaded, response.attempt());
            }
            loaded.verification().markSelfie(now);
            return captured(loaded, response.attempt());
        }
        return response;
    }

    @Transactional
    public UploadResponse selfieUpload(String token) {
        Loaded loaded = load(token);
        requireConsent(loaded.verification());
        if (liveIntegration(loaded.verification())) {
            throw ApiException.conflict("invalid_status", "Selfie capture is not expected");
        }
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
        if (liveIntegration(loaded.verification())) {
            throw ApiException.conflict("invalid_status", "Selfie capture is not expected");
        }
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
        return captured(loaded, response.attempt());
    }

    @Transactional
    public LivenessSessionResponse openLiveness(String token, boolean fresh) {
        Loaded loaded = load(token);
        requireLive(loaded);
        requireConsent(loaded.verification());
        requireFaceStep(loaded);
        if (billedLiveness(loaded) >= applied(loaded.verification()).maxLivenessAttempts()) {
            exhaustLiveness(loaded);
            throw ApiException.conflict("liveness_attempts_exceeded", "Liveness attempts exceeded");
        }
        Instant now = Instant.now();
        var open = livenessChecks.findFirstByVerificationIdAndStatusOrderByAttemptDesc(
                loaded.verification().getId(), LivenessCheckStatus.OPEN);
        if (open.isPresent()) {
            VerificationLivenessCheck check = open.get();
            if (!fresh && check.getExpiresAt().isAfter(now)) {
                return credentialsFor(check);
            }
            check.markExpired();
            livenessChecks.saveAndFlush(check);
        }
        int attempt = (int) livenessChecks.countByVerificationId(loaded.verification().getId()) + 1;
        String requestToken = loaded.verification().getId().toString().replace("-", "") + attempt;
        String sessionId;
        try {
            sessionId = faceLiveness.createSession(requestToken);
        } catch (ProviderUnavailableException e) {
            throw ApiException.dependencyUnavailable("Face Liveness unavailable");
        }
        VerificationLivenessCheck created = new VerificationLivenessCheck(
                UUID.randomUUID(),
                loaded.verification().getId(),
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
        log.info("Face Liveness session opened verificationId={} attempt={}", loaded.verification().getId(), attempt);
        return credentialsFor(created);
    }

    @Transactional
    public CompleteCaptureResponse completeLiveness(String token, LivenessCompleteRequest request) {
        Loaded loaded = load(token);
        requireLive(loaded);
        requireConsent(loaded.verification());
        requireFaceStep(loaded);
        if (billedLiveness(loaded) >= applied(loaded.verification()).maxLivenessAttempts()) {
            exhaustLiveness(loaded);
            throw ApiException.conflict("liveness_attempts_exceeded", "Liveness attempts exceeded");
        }
        VerificationLivenessCheck check = livenessChecks
                .findFirstByVerificationIdAndStatusOrderByAttemptDesc(
                        loaded.verification().getId(), LivenessCheckStatus.OPEN)
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
            commit(loaded, engine.providerUnavailable(), IdvDecisionEngine.BIO_RULES_VERSION, now);
            return finished(loaded, null);
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        String status = result == null ? "missing" : result.status();
        Double confidence = result == null ? null : result.confidence();
        log.info(
                "Face Liveness completed verificationId={} durationMs={} status={} confidence={} attempt={}",
                loaded.verification().getId(),
                durationMs,
                status,
                confidence,
                check.getAttempt());
        if (result == null || "EXPIRED".equalsIgnoreCase(result.status())) {
            check.markExpired();
            throw ApiException.conflict("liveness_expired", "Liveness session expired");
        }
        check.complete(confidence);
        byte[] reference = result.referenceImage() == null ? new byte[0] : result.referenceImage();
        storeReference(loaded, reference, check.getAttempt(), now);
        var document = accepted(loaded.verification().getId(), MediaKind.DOCUMENT)
                .orElseThrow(() -> ApiException.conflict("invalid_status", "Document is required"));
        try {
            BiometricAiPort.BiometricSignals signals =
                    liveBiometricAi.match(objectStorage.read(document.getObjectKey()), result);
            IdvDecisionEngine.Result face = engine.applyFace(
                    storedDocument(loaded.verification()), signals, applied(loaded.verification()).profile());
            if (retryLiveness(face) && billedLiveness(loaded) < applied(loaded.verification()).maxLivenessAttempts()) {
                return new CompleteCaptureResponse(
                        Enums.json(loaded.verification().getStatus()),
                        "liveness",
                        false,
                        check.getAttempt(),
                        true,
                        "liveness_fail");
            }
            commit(loaded, face, IdvDecisionEngine.BIO_RULES_VERSION, now);
        } catch (ProviderUnavailableException e) {
            commit(loaded, engine.providerUnavailable(), IdvDecisionEngine.BIO_RULES_VERSION, now);
        }
        return finished(loaded, null);
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

        commit(loaded, result, rulesVersion, now);
    }

    /**
     * Live only. A document that is not APPROVED ends the verification here.
     * Face Liveness is not opened. Sandbox keeps the selfie step.
     */
    private boolean finishDocumentBeforeFace(Loaded loaded, Instant now) {
        Integration integration = integrations
                .findById(loaded.verification().getIntegrationId())
                .orElse(null);
        if (integration == null || !integration.isLive()) {
            return false;
        }
        IdvDecisionEngine.Result document = storedDocument(loaded.verification());
        if (faceStepAllowed(document)) {
            return false;
        }
        commit(loaded, document, IdvDecisionEngine.VISION_RULES_VERSION, now);
        return true;
    }

    /** B3 calls {@link FaceLivenessClient#createSession()} only after this returns true. */
    private boolean faceStepAllowed(IdvDecisionEngine.Result document) {
        if (faceLiveness == null || document == null || document.decision() != VerificationDecision.APPROVED) {
            return false;
        }
        return true;
    }

    private IdvDecisionEngine.Result storedDocument(Verification verification) {
        IdvPolicyService.Applied applied = applied(verification);
        IdvDecisionEngine.Result front = storedOrUnavailable(verification.getId(), "FRONT", applied);
        if (front.decision() != VerificationDecision.APPROVED || !verification.isDocumentBackRequired()) {
            return front;
        }
        IdvDecisionEngine.Result back = storedBack(verification.getId(), applied);
        if (back.decision() == VerificationDecision.APPROVED) {
            return front;
        }
        VerificationDecision decision = back.decision() == null ? VerificationDecision.REVIEW : back.decision();
        return new IdvDecisionEngine.Result(decision, back.reasons(), back.signals(), front.extractedIdentity());
    }

    private IdvDecisionEngine.Result storedBack(UUID verificationId, IdvPolicyService.Applied applied) {
        var back = analyses.find(verificationId, "BACK");
        if (back.isEmpty()) {
            return engine.providerUnavailable();
        }
        var front = analyses.find(verificationId, "FRONT");
        if (front.isPresent() && !analyses.sameDocument(front.get(), back.get())) {
            return engine.review("DOCUMENT_MISMATCH");
        }
        return analyses.replay(back.get(), schemas, engine, applied.profile(), applied.acceptedCodes());
    }

    private IdvDecisionEngine.Result storedOrUnavailable(
            UUID verificationId, String side, IdvPolicyService.Applied applied) {
        return analyses
                .find(verificationId, side)
                .map(row -> analyses.replay(row, schemas, engine, applied.profile(), applied.acceptedCodes()))
                .orElseGet(engine::providerUnavailable);
    }

    private void commit(Loaded loaded, IdvDecisionEngine.Result result, String rulesVersion, Instant now) {
        Verification verification = loaded.verification();
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
        Integration identity = integrations
                .findById(verification.getIntegrationId())
                .orElseThrow(() -> ApiException.notFound("Integration not found"));
        amlAttachment.attach(verification, identity, now);
        audit(loaded, "verification.completed", "{\"decision\":\"" + Enums.json(verification.getDecision()) + "\"}", now);
        webhookService.enqueueCompleted(verification, now);
    }

    private IdvDecisionEngine.Result decideLive(UUID verificationId, byte[] documentBytes, byte[] selfieBytes, Instant now) {
        try {
            Verification verification = verifications.findById(verificationId).orElse(null);
            IdvPolicyService.Applied applied = policies.applied(verification == null ? null : verification.getPolicyId());
            IdvDecisionEngine.Result document = replayOrAnalyze(verificationId, documentBytes, now, applied);
            if (document.decision() != VerificationDecision.APPROVED) {
                return document;
            }
            if (verification != null && verification.isDocumentBackRequired()) {
                IdvDecisionEngine.Result back = storedBack(verificationId, applied);
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
            return engine.applyFace(document, bioSignals, applied.profile());
        } catch (ProviderUnavailableException e) {
            analysesBuffer.take();
            return engine.providerUnavailable();
        }
    }

    private IdvDecisionEngine.Result replayOrAnalyze(
            UUID verificationId, byte[] documentBytes, Instant now, IdvPolicyService.Applied applied) {
        var stored = analyses.find(verificationId, "FRONT");
        if (stored.isPresent()) {
            return analyses.replay(stored.get(), schemas, engine, applied.profile(), applied.acceptedCodes());
        }
        liveDocumentAi.analyze(documentBytes, null);
        DocumentLiveView view = analysesBuffer.take();
        if (view == null) {
            return engine.providerUnavailable();
        }
        analyses.saveAccepted(verificationId, view, "FRONT", now);
        var saved = analyses.find(verificationId, "FRONT");
        if (saved.isPresent()) {
            return analyses.replay(saved.get(), schemas, engine, applied.profile(), applied.acceptedCodes());
        }
        IdvDecisionEngine.Result result = view.result();
        if (result.decision() == null) {
            return new IdvDecisionEngine.Result(
                    VerificationDecision.REVIEW, result.reasons(), result.signals(), result.extractedIdentity());
        }
        return result;
    }

    private IdvPolicyService.Applied applied(Verification verification) {
        return policies.applied(verification == null ? null : verification.getPolicyId());
    }

    private boolean askForBack(Loaded loaded) {
        Integration integration = integrations
                .findById(loaded.verification().getIntegrationId())
                .orElse(null);
        if (integration == null || !integration.isLive()) {
            return false;
        }
        IdvPolicyService.Applied applied = applied(loaded.verification());
        var saved = analyses.find(loaded.verification().getId(), "FRONT");
        if (saved.isEmpty()) {
            return false;
        }
        IdvDecisionEngine.Result result =
                analyses.replay(saved.get(), schemas, engine, applied.profile(), applied.acceptedCodes());
        if (result == null || result.decision() != VerificationDecision.APPROVED) {
            return false;
        }
        Object code = result.extractedIdentity() == null ? null : result.extractedIdentity().get("document_code");
        return code instanceof String value && schemas.hasActiveSide(value, "BACK");
    }

    /**
     * Relit la capture déjà acceptée par la garde fichier. Tant qu'il reste des essais,
     * un refus (photo illisible, lecture non approuvée, fournisseur) redemande la même face.
     * Le dernier essai conserve la décision réelle.
     */
    private CompleteCaptureResponse rereadCapture(Loaded loaded, MediaKind kind, Integer attempt, Instant now) {
        if (!liveIntegration(loaded.verification()) || attempt == null || liveDocumentAi == null) {
            return null;
        }
        VerificationMedia item = media.findByVerificationIdAndKindAndAttempt(
                        loaded.verification().getId(), kind, attempt)
                .orElse(null);
        if (item == null || item.getStatus() != MediaStatus.ACCEPTED) {
            return null;
        }
        String side = kind == MediaKind.DOCUMENT_BACK ? "BACK" : "FRONT";
        try {
            DocumentLiveView view = liveDocumentAi.analyzeCapture(objectStorage.read(item.getObjectKey()), side);
            if (recaptureIssue(view)) {
                return rejectCapture(loaded, item, attempt, now, captureReason(view));
            }
            analyses.saveAccepted(loaded.verification().getId(), view, side, now);
            if (!view.accepted()) {
                return retrySide(loaded, item, attempt, now, side, captureReason(view));
            }
            IdvPolicyService.Applied applied = applied(loaded.verification());
            IdvDecisionEngine.Result result = analyses
                    .find(loaded.verification().getId(), side)
                    .map(row -> analyses.replay(row, schemas, engine, applied.profile(), applied.acceptedCodes()))
                    .orElse(null);
            if (result != null && result.decision() != VerificationDecision.APPROVED) {
                String reason = result.reasons() == null || result.reasons().isEmpty()
                        ? "LOW_CONFIDENCE"
                        : result.reasons().get(0);
                return retrySide(loaded, item, attempt, now, side, reason);
            }
            return null;
        } catch (ProviderUnavailableException e) {
            log.info("document-ia capture side={} code=provider_unavailable", side);
            return retrySide(loaded, item, attempt, now, side, "provider_unavailable");
        }
    }

    /** Redemande la face s'il reste un essai. Sinon laisse la décision se conclure. */
    private CompleteCaptureResponse retrySide(
            Loaded loaded, VerificationMedia item, int attempt, Instant now, String side, String reason) {
        if (!attemptsRemain(loaded, item.getKind())) {
            return null;
        }
        analyses.delete(loaded.verification().getId(), side);
        return rejectCapture(loaded, item, attempt, now, reason);
    }

    private CompleteCaptureResponse retryMismatch(Loaded loaded, Integer attempt, Instant now) {
        if (attempt == null || !attemptsRemain(loaded, MediaKind.DOCUMENT_BACK)) {
            return null;
        }
        var front = analyses.find(loaded.verification().getId(), "FRONT");
        var back = analyses.find(loaded.verification().getId(), "BACK");
        if (front.isEmpty() || back.isEmpty() || analyses.sameDocument(front.get(), back.get())) {
            return null;
        }
        VerificationMedia item = media.findByVerificationIdAndKindAndAttempt(
                        loaded.verification().getId(), MediaKind.DOCUMENT_BACK, attempt)
                .orElse(null);
        if (item == null || item.getStatus() != MediaStatus.ACCEPTED) {
            return null;
        }
        analyses.delete(loaded.verification().getId(), "BACK");
        return rejectCapture(loaded, item, attempt, now, "DOCUMENT_MISMATCH");
    }

    private boolean attemptsRemain(Loaded loaded, MediaKind kind) {
        return media.countByVerificationIdAndKind(loaded.verification().getId(), kind)
                < applied(loaded.verification()).maxCaptureAttempts();
    }

    private static boolean recaptureIssue(DocumentLiveView view) {
        return view != null
                && view.response().decision() instanceof DocumentDecision decision
                && "RECAPTURE".equals(decision.issue());
    }

    private static String captureReason(DocumentLiveView view) {
        if (view.response().decision() instanceof DocumentDecision decision
                && decision.reasons() != null
                && !decision.reasons().isEmpty()
                && decision.reasons().get(0) != null
                && !decision.reasons().get(0).isBlank()) {
            return decision.reasons().get(0);
        }
        if (view.response().quality() instanceof DocumentImageQuality quality
                && quality.reason() != null
                && !quality.reason().isBlank()) {
            return quality.reason();
        }
        return "IMAGE_TOO_BLURRY";
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
        if (used >= applied(loaded.verification()).maxCaptureAttempts()) {
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
            return rejectCapture(loaded, item, attempt, now, MediaQuality.rejectionReason(body));
        }
        item.accept(MediaQuality.sniff(body), body.length);
        return new CompleteCaptureResponse(
                Enums.json(loaded.verification().getStatus()), nextOf(loaded.verification()), true, attempt, false, null);
    }

    private CompleteCaptureResponse rejectCapture(
            Loaded loaded, VerificationMedia item, int attempt, Instant now, String reason) {
        item.rejectQuality();
        discardRejectedObject(item.getObjectKey());
        IdvPolicyService.Applied applied = applied(loaded.verification());
        if (media.countByVerificationIdAndKind(loaded.verification().getId(), item.getKind())
                >= applied.maxCaptureAttempts()) {
            loaded.verification().exhaustCaptureAttempts(now, applied.reviewOnCaptureExhausted());
            hostedTokens.revokeByVerificationId(loaded.verification().getId());
            audit(
                    loaded,
                    applied.reviewOnCaptureExhausted() ? "verification.completed" : "verification.declined",
                    "{\"reason\":\"capture_attempts_exceeded\"}",
                    now);
            return new CompleteCaptureResponse(
                    Enums.json(loaded.verification().getStatus()), "done", false, attempt, true, "capture_attempts_exceeded");
        }
        loaded.verification().markRecapture(now);
        return new CompleteCaptureResponse(
                Enums.json(loaded.verification().getStatus()),
                nextOf(loaded.verification()),
                false,
                attempt,
                true,
                reason);
    }

    private CompleteCaptureResponse captured(Loaded loaded, Integer attempt) {
        return new CompleteCaptureResponse(
                Enums.json(loaded.verification().getStatus()), nextOf(loaded.verification()), true, attempt, null, null);
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

    private void requireLive(Loaded loaded) {
        if (!liveIntegration(loaded.verification())) {
            throw ApiException.forbidden("sandbox_no_liveness", "Liveness is not available in sandbox");
        }
    }

    private void requireFaceStep(Loaded loaded) {
        if (loaded.verification().getStatus() != VerificationStatus.SELFIE
                || backStillRequired(loaded.verification())
                || !faceStepAllowed(storedDocument(loaded.verification()))) {
            throw ApiException.conflict("invalid_status", "Liveness is not expected");
        }
    }

    private static boolean retryLiveness(IdvDecisionEngine.Result face) {
        return face != null
                && face.decision() == VerificationDecision.DECLINED
                && face.reasons() != null
                && face.reasons().contains("liveness_fail");
    }

    private long billedLiveness(Loaded loaded) {
        return livenessChecks.countByVerificationIdAndBilledTrue(loaded.verification().getId());
    }

    private void exhaustLiveness(Loaded loaded) {
        if (loaded.verification().terminal()) {
            return;
        }
        commit(
                loaded,
                new IdvDecisionEngine.Result(
                        VerificationDecision.DECLINED,
                        List.of("liveness_fail"),
                        List.of(new IdvDecisionEngine.Signal("liveness_fail", SignalOutcome.FAIL, null)),
                        Map.of()),
                IdvDecisionEngine.BIO_RULES_VERSION,
                Instant.now());
    }

    private LivenessSessionResponse credentialsFor(VerificationLivenessCheck check) {
        try {
            LivenessCredentialPort.Issued issued = livenessCredentials.issue();
            return new LivenessSessionResponse(
                    check.getSessionId(),
                    check.getRegion(),
                    new LivenessSessionResponse.Credentials(
                            issued.accessKeyId(), issued.secretAccessKey(), issued.sessionToken(), issued.expiration()));
        } catch (ProviderUnavailableException e) {
            throw ApiException.dependencyUnavailable("Face Liveness unavailable");
        }
    }

    private void storeReference(Loaded loaded, byte[] reference, int attempt, Instant now) {
        if (reference.length == 0) {
            return;
        }
        String type = MediaQuality.sniff(reference);
        if (type == null) {
            type = "image/jpeg";
        }
        String objectKey = "org/"
                + loaded.organizationId()
                + "/verifications/"
                + loaded.verification().getId()
                + "/selfie/"
                + attempt;
        objectStorage.write(objectKey, reference, type);
        VerificationMedia item = new VerificationMedia(
                UUID.randomUUID(), loaded.verification().getId(), MediaKind.SELFIE, attempt, objectKey, now);
        item.accept(type, reference.length);
        media.save(item);
    }

    private CompleteCaptureResponse finished(Loaded loaded, Integer attempt) {
        return captured(loaded, attempt);
    }

    private boolean liveIntegration(Verification verification) {
        return integrations.findById(verification.getIntegrationId()).map(Integration::isLive).orElse(false);
    }

    private String faceNext(Verification verification) {
        return liveIntegration(verification) ? "liveness" : "capture_selfie";
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
                yield faceNext(verification);
            }
            case SELFIE -> faceNext(verification);
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
