package com.kyc.entities;

import com.kyc.enums.AuthenticationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "authentications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthenticationSession {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "integration_id", nullable = false)
    private UUID integrationId;

    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "face_id", nullable = false, length = 128)
    private String faceId;

    @Column(nullable = false, length = 16)
    private String engine;

    @Column(name = "policy_version", nullable = false, length = 32)
    private String policyVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuthenticationStatus status;

    @Column(length = 256)
    private String reasons;

    @Column(name = "failure_reason", length = 64)
    private String failureReason;

    @Column(name = "match_score", precision = 4, scale = 3)
    private BigDecimal matchScore;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "text")
    private String metadata;

    @Column(name = "consent_version", length = 32)
    private String consentVersion;

    @Column(name = "consent_at")
    private Instant consentAt;

    @Column(name = "hosted_token_hash", nullable = false, length = 64)
    private String hostedTokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    public AuthenticationSession(
            UUID id,
            UUID organizationId,
            UUID integrationId,
            String externalId,
            UUID enrollmentId,
            String faceId,
            String engine,
            String policyVersion,
            String metadata,
            String hostedTokenHash,
            Instant expiresAt,
            Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.integrationId = integrationId;
        this.externalId = externalId;
        this.enrollmentId = enrollmentId;
        this.faceId = faceId;
        this.engine = engine;
        this.policyVersion = policyVersion;
        this.status = AuthenticationStatus.CREATED;
        this.metadata = metadata;
        this.hostedTokenHash = hostedTokenHash;
        this.expiresAt = expiresAt;
        this.attempts = 0;
        this.createdAt = createdAt;
    }

    public boolean terminal() {
        return status == AuthenticationStatus.APPROVED
                || status == AuthenticationStatus.DECLINED
                || status == AuthenticationStatus.FAILED
                || status == AuthenticationStatus.EXPIRED
                || status == AuthenticationStatus.CANCELLED;
    }

    public boolean cancelable() {
        return !terminal();
    }

    public boolean expired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public void markOpened(Instant now) {
        if (status == AuthenticationStatus.CREATED) {
            this.status = AuthenticationStatus.PENDING_CONSENT;
        }
    }

    public void acceptConsent(String version, Instant now) {
        this.consentVersion = version;
        this.consentAt = now;
        this.status = AuthenticationStatus.SELFIE;
    }

    public void refuseConsent(String version, String reasonsJson, Instant now) {
        this.consentVersion = version;
        this.consentAt = now;
        applyDecision(AuthenticationStatus.DECLINED, reasonsJson, null, null, now);
    }

    public void requestRecapture() {
        this.attempts = this.attempts + 1;
        this.status = AuthenticationStatus.RECAPTURE_REQUESTED;
    }

    public void markProcessing() {
        this.status = AuthenticationStatus.PROCESSING;
    }

    public void cancel(Instant now) {
        this.status = AuthenticationStatus.CANCELLED;
        this.decidedAt = now;
    }

    public void expire(Instant now) {
        this.status = AuthenticationStatus.EXPIRED;
        this.decidedAt = now;
    }

    public void applyDecision(
            AuthenticationStatus status, String reasons, BigDecimal matchScore, String failureReason, Instant now) {
        this.status = status;
        this.reasons = reasons;
        this.matchScore = matchScore;
        this.failureReason = failureReason;
        this.decidedAt = now;
    }

    /** Analyst decision. Keeps the machine score and appends the analyst reason. */
    public void applyReview(AuthenticationStatus status, String reasons, Instant now) {
        this.status = status;
        this.reasons = reasons;
        this.decidedAt = now;
    }
}
