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
import com.kyc.enums.VerificationStatus;
import com.kyc.ports.BiometricAiPort;
import com.kyc.ports.DocumentAiPort;
import com.kyc.ports.HostedTokenStore;
import com.kyc.ports.ObjectStoragePort;
import com.kyc.ports.ProviderUnavailableException;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HostedFlowService {

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
    private final DocumentAiPort liveDocumentAi;
    private final BiometricAiPort stubBiometricAi;
    private final BiometricAiPort liveBiometricAi;
    private final IdvDecisionEngine engine;
    private final KycProperties properties;
    private final ObjectMapper objectMapper;
    private final WebhookService webhookService;

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
            @Qualifier("liveDocumentAi") DocumentAiPort liveDocumentAi,
            @Qualifier("stubBiometricAi") BiometricAiPort stubBiometricAi,
            @Qualifier("liveBiometricAi") BiometricAiPort liveBiometricAi,
            KycProperties properties,
            ObjectMapper objectMapper,
            WebhookService webhookService) {
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
        this.stubBiometricAi = stubBiometricAi;
        this.liveBiometricAi = liveBiometricAi;
        this.engine = new IdvDecisionEngine();
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webhookService = webhookService;
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
        if (!DOCUMENT_UPLOAD.contains(loaded.verification().getStatus())
                || accepted(loaded.verification().getId(), MediaKind.DOCUMENT).isPresent()) {
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
        MediaKind kind = "back".equals(request == null ? null : request.side())
                ? MediaKind.DOCUMENT_BACK
                : MediaKind.DOCUMENT;
        CompleteCaptureResponse response = completeMedia(loaded, kind, request == null ? null : request.attempt(), MediaQuality::acceptableDocument);
        if (Boolean.TRUE.equals(response.accepted()) && kind == MediaKind.DOCUMENT) {
            Instant now = Instant.now();
            loaded.verification().markSelfie(now);
            return new CompleteCaptureResponse(
                    Enums.json(loaded.verification().getStatus()), nextOf(loaded.verification()), true, response.attempt(), null);
        }
        return response;
    }

    @Transactional
    public UploadResponse selfieUpload(String token) {
        Loaded loaded = load(token);
        requireConsent(loaded.verification());
        if (!SELFIE_UPLOAD.contains(loaded.verification().getStatus())
                || accepted(loaded.verification().getId(), MediaKind.DOCUMENT).isEmpty()
                || accepted(loaded.verification().getId(), MediaKind.SELFIE).isPresent()) {
            throw ApiException.conflict("invalid_status", "Selfie capture is not expected");
        }
        return issueUpload(loaded, MediaKind.SELFIE);
    }

    @Transactional
    public CompleteCaptureResponse selfieComplete(String token, CompleteUploadRequest request) {
        Loaded loaded = load(token);
        requireConsent(loaded.verification());
        if (!SELFIE_UPLOAD.contains(loaded.verification().getStatus())
                || accepted(loaded.verification().getId(), MediaKind.DOCUMENT).isEmpty()) {
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
            rulesVersion = IdvDecisionEngine.LIVE_RULES_VERSION;
            result = decideLive(documentBytes, selfieBytes);
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

    private IdvDecisionEngine.Result decideLive(byte[] documentBytes, byte[] selfieBytes) {
        try {
            DocumentAiPort.DocumentSignals docSignals = liveDocumentAi.analyze(documentBytes, null);
            if (!docSignals.supported()) {
                return engine.decideLive(docSignals, null);
            }
            BiometricAiPort.BiometricSignals bioSignals =
                    liveBiometricAi.evaluate(documentBytes, selfieBytes, null);
            return engine.decideLive(docSignals, bioSignals);
        } catch (ProviderUnavailableException e) {
            return engine.providerUnavailable();
        }
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
                if (accepted(verification.getId(), MediaKind.DOCUMENT).isPresent()
                        && accepted(verification.getId(), MediaKind.SELFIE).isEmpty()) {
                    yield "capture_selfie";
                }
                if (accepted(verification.getId(), MediaKind.DOCUMENT).isEmpty()) {
                    yield "capture_document";
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
