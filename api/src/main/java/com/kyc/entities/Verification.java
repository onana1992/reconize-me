package com.kyc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
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

    public static final String CREATED = "created";
    public static final String PENDING_CONSENT = "pending_consent";
    public static final String PENDING_APPLICANT = "pending_applicant";
    public static final String DOCUMENT = "document";
    public static final String RECAPTURE_REQUESTED = "recapture_requested";
    public static final String SELFIE = "selfie";
    public static final String PROCESSING = "processing";
    public static final String REVIEW = "review";
    public static final String APPROVED = "approved";
    public static final String DECLINED = "declined";
    public static final String EXPIRED = "expired";
    public static final String CANCELLED = "cancelled";

    private static final Set<String> CANCELABLE = Set.of(
            CREATED, PENDING_CONSENT, PENDING_APPLICANT, DOCUMENT, RECAPTURE_REQUESTED, SELFIE);

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false, length = 32)
    private String status;

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

    @Column
    private String decision;

    @Column(name = "decision_reasons")
    private String decisionReasons;

    @Column(name = "rules_version")
    private String rulesVersion;

    @Column(name = "sandbox_scenario")
    private String sandboxScenario;

    @Column(name = "extracted_identity")
    private String extractedIdentity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Verification(
            UUID id,
            UUID organizationId,
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
        this.externalId = externalId;
        this.status = CREATED;
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

    public boolean expired(Instant now) {
        return now.isAfter(hostedExpiresAt) || EXPIRED.equals(status);
    }

    public void markOpened(Instant now) {
        if (CREATED.equals(status)) {
            this.status = PENDING_CONSENT;
            this.updatedAt = now;
        }
    }

    public void markConsentAccepted(Instant now) {
        this.status = PENDING_APPLICANT;
        this.updatedAt = now;
    }

    public void markConsentDeclined(Instant now) {
        this.status = DECLINED;
        this.decision = DECLINED;
        this.decisionReasons = "[\"consent_declined\"]";
        this.updatedAt = now;
    }

    public void markDocument(Instant now) {
        this.status = DOCUMENT;
        this.updatedAt = now;
    }

    public void markRecapture(Instant now) {
        this.status = RECAPTURE_REQUESTED;
        this.updatedAt = now;
    }

    public void markSelfie(Instant now) {
        this.status = SELFIE;
        this.updatedAt = now;
    }

    public void markProcessing(Instant now) {
        this.status = PROCESSING;
        this.updatedAt = now;
    }

    public void decide(String decision, String reasonsJson, String rulesVersion, String extractedIdentity, Instant now) {
        this.decision = decision;
        this.decisionReasons = reasonsJson;
        this.rulesVersion = rulesVersion;
        this.extractedIdentity = extractedIdentity;
        this.status = decision;
        this.updatedAt = now;
    }

    public void applyReview(String decision, Instant now) {
        this.decision = decision;
        this.status = decision;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        this.status = CANCELLED;
        this.updatedAt = now;
    }

    public void expire(Instant now) {
        this.status = EXPIRED;
        this.updatedAt = now;
    }

    public void declineCaptureAttempts(Instant now) {
        this.status = DECLINED;
        this.decision = DECLINED;
        this.decisionReasons = "[\"capture_attempts_exceeded\"]";
        this.updatedAt = now;
    }
}
