package com.kyc.entities;

import com.kyc.enums.VerificationDecision;
import com.kyc.enums.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Verification {

    private static final Set<VerificationStatus> CANCELABLE = EnumSet.of(
            VerificationStatus.CREATED,
            VerificationStatus.PENDING_CONSENT,
            VerificationStatus.PENDING_APPLICANT,
            VerificationStatus.DOCUMENT,
            VerificationStatus.RECAPTURE_REQUESTED,
            VerificationStatus.SELFIE);

    private static final Set<VerificationStatus> TERMINAL = EnumSet.of(
            VerificationStatus.REVIEW,
            VerificationStatus.APPROVED,
            VerificationStatus.DECLINED,
            VerificationStatus.EXPIRED,
            VerificationStatus.CANCELLED);

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "integration_id", nullable = false)
    private UUID integrationId;

    @Column(name = "external_id")
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VerificationStatus status;

    @Column(name = "applicant_first_name")
    private String applicantFirstName;

    @Column(name = "applicant_last_name")
    private String applicantLastName;

    @Column(name = "applicant_email")
    private String applicantEmail;

    @Column
    private String metadata;

    @Column(name = "hosted_token_hash", nullable = false, length = 64)
    private String hostedTokenHash;

    @Column(name = "hosted_expires_at", nullable = false)
    private Instant hostedExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private VerificationDecision decision;

    @Column(name = "decision_reasons")
    private String decisionReasons;

    @Column(name = "rules_version")
    private String rulesVersion;

    @Column(name = "sandbox_scenario")
    private String sandboxScenario;

    @Column(name = "extracted_identity", columnDefinition = "TEXT")
    private String extractedIdentity;

    @Column(name = "document_back_required", nullable = false)
    private boolean documentBackRequired;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Verification(
            UUID id,
            UUID organizationId,
            UUID integrationId,
            String externalId,
            String applicantFirstName,
            String applicantLastName,
            String applicantEmail,
            String metadata,
            String hostedTokenHash,
            Instant hostedExpiresAt,
            String sandboxScenario,
            Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.integrationId = integrationId;
        this.externalId = externalId;
        this.status = VerificationStatus.CREATED;
        this.applicantFirstName = applicantFirstName;
        this.applicantLastName = applicantLastName;
        this.applicantEmail = applicantEmail;
        this.metadata = metadata;
        this.hostedTokenHash = hostedTokenHash;
        this.hostedExpiresAt = hostedExpiresAt;
        this.sandboxScenario = sandboxScenario;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean cancelable() {
        return CANCELABLE.contains(status);
    }

    public boolean terminal() {
        return TERMINAL.contains(status);
    }

    public boolean expired(Instant now) {
        return now.isAfter(hostedExpiresAt) || status == VerificationStatus.EXPIRED;
    }

    public void markOpened(Instant now) {
        if (status == VerificationStatus.CREATED) {
            this.status = VerificationStatus.PENDING_CONSENT;
            this.updatedAt = now;
        }
    }

    public void markConsentAccepted(Instant now) {
        this.status = VerificationStatus.PENDING_APPLICANT;
        this.updatedAt = now;
    }

    public void markConsentDeclined(Instant now) {
        this.status = VerificationStatus.DECLINED;
        this.decision = VerificationDecision.DECLINED;
        this.decisionReasons = "[\"consent_declined\"]";
        this.updatedAt = now;
    }

    public void markDocument(Instant now) {
        this.status = VerificationStatus.DOCUMENT;
        this.updatedAt = now;
    }

    public void markRecapture(Instant now) {
        this.status = VerificationStatus.RECAPTURE_REQUESTED;
        this.updatedAt = now;
    }

    public void requireDocumentBack(Instant now) {
        this.documentBackRequired = true;
        this.updatedAt = now;
    }

    public void markSelfie(Instant now) {
        this.status = VerificationStatus.SELFIE;
        this.updatedAt = now;
    }

    public void markProcessing(Instant now) {
        this.status = VerificationStatus.PROCESSING;
        this.updatedAt = now;
    }

    public void decide(
            VerificationDecision decision, String reasonsJson, String rulesVersion, String extractedIdentity, Instant now) {
        this.decision = decision;
        this.decisionReasons = reasonsJson;
        this.rulesVersion = rulesVersion;
        this.extractedIdentity = extractedIdentity;
        this.status = VerificationStatus.valueOf(decision.name());
        this.updatedAt = now;
    }

    public void applyReview(VerificationDecision decision, Instant now) {
        this.decision = decision;
        this.status = VerificationStatus.valueOf(decision.name());
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        this.status = VerificationStatus.CANCELLED;
        this.updatedAt = now;
    }

    public void expire(Instant now) {
        this.status = VerificationStatus.EXPIRED;
        this.updatedAt = now;
    }

    public void declineCaptureAttempts(Instant now) {
        this.status = VerificationStatus.DECLINED;
        this.decision = VerificationDecision.DECLINED;
        this.decisionReasons = "[\"capture_attempts_exceeded\"]";
        this.updatedAt = now;
    }
}
