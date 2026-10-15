package com.kyc.entities;

import com.kyc.enums.LivenessCheckStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "verification_liveness_checks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationLivenessCheck {

    @Id
    private UUID id;

    @Column(name = "verification_id", nullable = false)
    private UUID verificationId;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false, length = 32)
    private String region;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LivenessCheckStatus status;

    private Double confidence;

    @Column(nullable = false)
    private boolean billed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public VerificationLivenessCheck(
            UUID id,
            UUID verificationId,
            int attempt,
            String sessionId,
            String region,
            Instant expiresAt,
            Instant createdAt) {
        this.id = id;
        this.verificationId = verificationId;
        this.attempt = attempt;
        this.sessionId = sessionId;
        this.region = region;
        this.expiresAt = expiresAt;
        this.status = LivenessCheckStatus.OPEN;
        this.billed = false;
        this.createdAt = createdAt;
    }

    public void markExpired() {
        this.status = LivenessCheckStatus.EXPIRED;
    }

    public void forceExpiry(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void complete(double confidence) {
        this.status = LivenessCheckStatus.COMPLETED;
        this.confidence = confidence;
        this.billed = true;
    }
}
