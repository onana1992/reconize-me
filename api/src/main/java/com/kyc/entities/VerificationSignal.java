package com.kyc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "verification_signals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationSignal {

    @Id
    private UUID id;

    @Column(name = "verification_id", nullable = false)
    private UUID verificationId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 32)
    private String outcome;

    @Column
    private Double score;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public VerificationSignal(UUID id, UUID verificationId, String code, String outcome, Double score, Instant createdAt) {
        this.id = id;
        this.verificationId = verificationId;
        this.code = code;
        this.outcome = outcome;
        this.score = score;
        this.createdAt = createdAt;
    }
}
