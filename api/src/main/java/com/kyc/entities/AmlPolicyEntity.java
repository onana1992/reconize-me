package com.kyc.entities;

import com.kyc.enums.AmlOnMatch;
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
import lombok.Getter;

@Entity
@Table(
        name = "aml_policies",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_aml_policies_integration_version",
                    columnNames = {"integration_id", "version"}),
            @UniqueConstraint(name = "uq_aml_policies_active", columnNames = "active_marker")
        })
@Getter
public class AmlPolicyEntity {

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

    @Column(name = "review_threshold", nullable = false, precision = 4, scale = 3)
    private BigDecimal reviewThreshold;

    @Column(name = "confirm_threshold", nullable = false, precision = 4, scale = 3)
    private BigDecimal confirmThreshold;

    @Column(nullable = false, length = 128)
    private String categories;

    @Column(name = "attach_to_identity", nullable = false)
    private boolean attachToIdentity;

    @Enumerated(EnumType.STRING)
    @Column(name = "on_match", nullable = false, length = 16)
    private AmlOnMatch onMatch;

    @Column(name = "test_integration_id")
    private UUID testIntegrationId;

    @Column(name = "live_integration_id")
    private UUID liveIntegrationId;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "published_by", nullable = false)
    private UUID publishedBy;

    protected AmlPolicyEntity() {}

    public AmlPolicyEntity(
            UUID id,
            UUID organizationId,
            UUID integrationId,
            int version,
            BigDecimal reviewThreshold,
            BigDecimal confirmThreshold,
            String categories,
            boolean attachToIdentity,
            AmlOnMatch onMatch,
            UUID testIntegrationId,
            UUID liveIntegrationId,
            UUID publishedBy,
            Instant publishedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.integrationId = integrationId;
        this.version = version;
        this.active = true;
        this.activeMarker = integrationId.toString();
        this.reviewThreshold = reviewThreshold;
        this.confirmThreshold = confirmThreshold;
        this.categories = categories;
        this.attachToIdentity = attachToIdentity;
        this.onMatch = onMatch;
        this.testIntegrationId = testIntegrationId;
        this.liveIntegrationId = liveIntegrationId;
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
    }

    public void retire() {
        this.active = false;
        this.activeMarker = null;
    }
}
