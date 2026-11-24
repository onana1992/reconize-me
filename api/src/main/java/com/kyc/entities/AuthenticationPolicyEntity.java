package com.kyc.entities;

import com.kyc.enums.AuthOnBorderline;
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
        name = "authentication_policies",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_authentication_policies_integration_version",
                    columnNames = {"integration_id", "version"}),
            @UniqueConstraint(name = "uq_authentication_policies_active", columnNames = "active_marker")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthenticationPolicyEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "integration_id")
    private UUID integrationId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "active_marker", length = 36)
    private String activeMarker;

    @Column(name = "approval_threshold", nullable = false, precision = 4, scale = 3)
    private BigDecimal approvalThreshold;

    @Column(name = "review_threshold", nullable = false, precision = 4, scale = 3)
    private BigDecimal reviewThreshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "on_borderline", nullable = false, length = 16)
    private AuthOnBorderline onBorderline;

    @Column(name = "max_capture_attempts", nullable = false)
    private int maxCaptureAttempts;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "published_by", nullable = false)
    private UUID publishedBy;

    public AuthenticationPolicyEntity(
            UUID id,
            UUID organizationId,
            UUID integrationId,
            int version,
            BigDecimal approvalThreshold,
            BigDecimal reviewThreshold,
            AuthOnBorderline onBorderline,
            int maxCaptureAttempts,
            UUID publishedBy,
            Instant publishedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.integrationId = integrationId;
        this.version = version;
        this.active = true;
        this.activeMarker = integrationId == null ? null : integrationId.toString();
        this.approvalThreshold = approvalThreshold;
        this.reviewThreshold = reviewThreshold;
        this.onBorderline = onBorderline;
        this.maxCaptureAttempts = maxCaptureAttempts;
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
    }

    public void retire() {
        this.active = false;
        this.activeMarker = null;
    }
}
