package com.kyc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "verification_authenticity_assessments")
@IdClass(VerificationAuthenticityAssessmentKey.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationAuthenticityAssessment {

    @Id
    @Column(name = "verification_id")
    private UUID verificationId;

    @Id
    @Column(nullable = false, length = 16)
    private String side;

    @Column(name = "engine_version", nullable = false, length = 32)
    private String engineVersion;

    @Column(name = "fusion_version", nullable = false, length = 32)
    private String fusionVersion;

    @Column(name = "forensic_usable", nullable = false)
    private boolean forensicUsable;

    @Column(name = "authenticity_score")
    private Double authenticityScore;

    @Column(name = "tampering_score")
    private Double tamperingScore;

    @Column(name = "presentation_score")
    private Double presentationScore;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "alerts_json", columnDefinition = "text")
    private String alertsJson;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public VerificationAuthenticityAssessment(
            UUID verificationId,
            String side,
            String engineVersion,
            String fusionVersion,
            boolean forensicUsable,
            Double authenticityScore,
            Double tamperingScore,
            Double presentationScore,
            String alertsJson,
            long durationMs,
            Instant createdAt) {
        this.verificationId = verificationId;
        this.side = side;
        this.engineVersion = engineVersion;
        this.fusionVersion = fusionVersion;
        this.forensicUsable = forensicUsable;
        this.authenticityScore = authenticityScore;
        this.tamperingScore = tamperingScore;
        this.presentationScore = presentationScore;
        this.alertsJson = alertsJson;
        this.durationMs = durationMs;
        this.createdAt = createdAt;
    }
}
