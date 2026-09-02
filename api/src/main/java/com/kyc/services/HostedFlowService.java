package com.kyc.services;

import com.kyc.config.KycProperties;
import com.kyc.dto.ConsentResponse;
import com.kyc.dto.FlowSessionResponse;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.Consent;
import com.kyc.entities.ConsentDecision;
import com.kyc.entities.Verification;
import com.kyc.entities.VerificationStatus;
import com.kyc.ports.HostedTokenStore;
import com.kyc.ports.HostedTokenStore.HostedSession;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.ConsentRepository;
import com.kyc.repositories.VerificationRepository;
import com.kyc.web.ApiException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HostedFlowService {

    private static final Logger log = LoggerFactory.getLogger(HostedFlowService.class);

    private final HostedTokenStore hostedTokenStore;
    private final VerificationRepository verificationRepository;
    private final ConsentRepository consentRepository;
    private final AuditEventRepository auditEventRepository;
    private final KycProperties properties;

    public HostedFlowService(
            HostedTokenStore hostedTokenStore,
            VerificationRepository verificationRepository,
            ConsentRepository consentRepository,
            AuditEventRepository auditEventRepository,
            KycProperties properties) {
        this.hostedTokenStore = hostedTokenStore;
        this.verificationRepository = verificationRepository;
        this.consentRepository = consentRepository;
        this.auditEventRepository = auditEventRepository;
        this.properties = properties;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public FlowSessionResponse open(String token) {
        Verification verification = resolveLive(token);
        Instant now = Instant.now();
        if (verification.getStatus() == VerificationStatus.CREATED) {
            verification.transitionTo(VerificationStatus.PENDING_CONSENT, now);
            audit(
                    verification,
                    "hosted_link.opened",
                    "{}");
            log.info(
                    "hosted link opened organization_id={} verification_id={}",
                    verification.getOrganizationId(),
                    verification.getId());
        }
        return toSession(verification);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ConsentResponse consent(String token, String decision, String ipAddress, String userAgent) {
        Verification verification = resolveLive(token);
        UUID organizationId = verification.getOrganizationId();
        if (consentRepository.existsByVerificationIdAndOrganizationId(verification.getId(), organizationId)
                || verification.getStatus() == VerificationStatus.PENDING_APPLICANT
                || verification.getStatus() == VerificationStatus.DECLINED) {
            throw ApiException.conflict("consent_already_recorded", "Consent already recorded");
        }
        if (verification.getStatus() != VerificationStatus.CREATED
                && verification.getStatus() != VerificationStatus.PENDING_CONSENT) {
            throw ApiException.conflict("consent_already_recorded", "Consent already recorded");
        }

        Instant now = Instant.now();
        boolean accepted = "accepted".equals(decision);
        ConsentDecision consentDecision = accepted ? ConsentDecision.ACCEPTED : ConsentDecision.DECLINED;
        VerificationStatus next =
                accepted ? VerificationStatus.PENDING_APPLICANT : VerificationStatus.DECLINED;
        verification.transitionTo(next, now);

        String ipHash = CryptoTokens.sha256Hex(safeIp(ipAddress) + properties.ipHashPepper());
        String ua = truncate(userAgent, 512);
        consentRepository.save(new Consent(
                UUID.randomUUID(),
                organizationId,
                verification.getId(),
                consentDecision,
                properties.consentTextVersion(),
                accepted ? now : null,
                ipHash,
                ua,
                now));
        audit(
                verification,
                accepted ? "consent.accepted" : "consent.declined",
                "{\"text_version\":\"" + properties.consentTextVersion() + "\"}");
        log.info(
                "consent recorded organization_id={} verification_id={} decision={}",
                organizationId,
                verification.getId(),
                decision);
        return new ConsentResponse(next.name().toLowerCase(Locale.ROOT), "capture_unavailable");
    }

    private Verification resolveLive(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.notFound("Hosted link not found");
        }
        Instant now = Instant.now();
        return hostedTokenStore
                .findSession(token)
                .map(session -> liveFromSession(session, now))
                .orElseGet(() -> expiredOrUnknown(token, now));
    }

    private Verification liveFromSession(HostedSession session, Instant now) {
        Verification verification = verificationRepository
                .findByIdAndOrganizationId(session.verificationId(), session.organizationId())
                .orElseThrow(() -> ApiException.notFound("Hosted link not found"));
        if (isExpired(verification, now)) {
            markExpired(verification, now);
            throw ApiException.gone("hosted_link_expired", "Hosted link expired");
        }
        return verification;
    }

    private Verification expiredOrUnknown(String token, Instant now) {
        Verification verification = verificationRepository
                .findByHostedTokenHash(CryptoTokens.sha256Hex(token))
                .orElseThrow(() -> ApiException.notFound("Hosted link not found"));
        markExpired(verification, now);
        throw ApiException.gone("hosted_link_expired", "Hosted link expired");
    }

    private void markExpired(Verification verification, Instant now) {
        if (verification.getStatus() == VerificationStatus.CREATED
                || verification.getStatus() == VerificationStatus.PENDING_CONSENT
                || verification.getStatus() == VerificationStatus.PENDING_APPLICANT) {
            verification.transitionTo(VerificationStatus.EXPIRED, now);
            audit(verification, "verification.expired", "{}");
        }
    }

    private static boolean isExpired(Verification verification, Instant now) {
        return verification.getStatus() == VerificationStatus.EXPIRED
                || now.isAfter(verification.getHostedExpiresAt());
    }

    private FlowSessionResponse toSession(Verification verification) {
        return new FlowSessionResponse(
                verification.getId(),
                verification.getStatus().name().toLowerCase(Locale.ROOT),
                properties.consentTextVersion(),
                verification.getHostedExpiresAt());
    }

    private void audit(Verification verification, String action, String payload) {
        auditEventRepository.save(new AuditEvent(
                verification.getOrganizationId(),
                "applicant",
                null,
                action,
                action.startsWith("consent") ? "consent" : "verification",
                verification.getId(),
                payload,
                Instant.now()));
    }

    private static String safeIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return "unknown";
        }
        return ipAddress.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
