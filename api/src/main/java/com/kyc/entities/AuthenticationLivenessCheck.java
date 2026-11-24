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
@Table(name = "authentication_liveness_checks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthenticationLivenessCheck {

    @Id
    private UUID id;

    @Column(name = "authentication_id", nullable = false)
    private UUID authenticationId;

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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AuthenticationLivenessCheck(
            UUID id,
            UUID authenticationId,
            int attempt,
            String sessionId,
            String region,
            Instant expiresAt,
            Instant createdAt) {
        this.id = id;
        this.authenticationId = authenticationId;
        this.attempt = attempt;
        this.sessionId = sessionId;
        this.region = region;
        this.expiresAt = expiresAt;
        this.status = LivenessCheckStatus.OPEN;
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
    }
}
