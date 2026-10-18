package com.kyc.entities;

import com.kyc.enums.VerificationDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "idv_policies",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_idv_policies_org_version", columnNames = {"organization_id", "version"}),
            @UniqueConstraint(name = "uq_idv_policies_active", columnNames = "active_marker")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdvPolicy {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "active_marker", length = 36)
    private String activeMarker;

    @Enumerated(EnumType.STRING)
    @Column(name = "expired_outcome", nullable = false, length = 16)
    private VerificationDecision expiredOutcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempts_outcome", nullable = false, length = 16)
    private VerificationDecision attemptsOutcome;

    @Column(name = "ocr_review_at", nullable = false, precision = 4, scale = 3)
    private BigDecimal ocrReviewAt;

    @Column(name = "liveness_pass_at", nullable = false, precision = 4, scale = 3)
    private BigDecimal livenessPassAt;

    @Column(name = "face_match_fail_at", nullable = false, precision = 4, scale = 3)
    private BigDecimal faceMatchFailAt;

    @Column(name = "face_match_pass_at", nullable = false, precision = 4, scale = 3)
    private BigDecimal faceMatchPassAt;

    @Column(name = "link_ttl_seconds", nullable = false)
    private int linkTtlSeconds;

    @Column(name = "max_capture_attempts", nullable = false)
    private int maxCaptureAttempts;

    @Column(name = "max_liveness_attempts", nullable = false)
    private int maxLivenessAttempts;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "published_by", nullable = false)
    private UUID publishedBy;

    public IdvPolicy(
            UUID id,
            UUID organizationId,
            int version,
            VerificationDecision expiredOutcome,
            VerificationDecision attemptsOutcome,
            BigDecimal ocrReviewAt,
            BigDecimal livenessPassAt,
            BigDecimal faceMatchFailAt,
            BigDecimal faceMatchPassAt,
            int linkTtlSeconds,
            int maxCaptureAttempts,
            int maxLivenessAttempts,
            UUID publishedBy,
            Instant publishedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.version = version;
        this.active = true;
        this.activeMarker = organizationId.toString();
        this.expiredOutcome = expiredOutcome;
        this.attemptsOutcome = attemptsOutcome;
        this.ocrReviewAt = ocrReviewAt;
        this.livenessPassAt = livenessPassAt;
        this.faceMatchFailAt = faceMatchFailAt;
        this.faceMatchPassAt = faceMatchPassAt;
        this.linkTtlSeconds = linkTtlSeconds;
        this.maxCaptureAttempts = maxCaptureAttempts;
        this.maxLivenessAttempts = maxLivenessAttempts;
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
    }

    public void retire() {
        this.active = false;
        this.activeMarker = null;
    }
}
