package com.kyc.entities;

import com.kyc.enums.EnrollmentSource;
import com.kyc.enums.EnrollmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "enrollments",
        uniqueConstraints = @UniqueConstraint(name = "uq_enrollments_active", columnNames = "active_marker"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EnrollmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EnrollmentSource source;

    @Column(name = "source_verification_id", nullable = false)
    private UUID sourceVerificationId;

    @Column(name = "face_id", length = 128)
    private String faceId;

    @Column(name = "collection_id", length = 128)
    private String collectionId;

    @Column(name = "active_marker", length = 200)
    private String activeMarker;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public Enrollment(
            UUID id,
            UUID organizationId,
            String externalId,
            EnrollmentSource source,
            UUID sourceVerificationId,
            String faceId,
            String collectionId,
            Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.externalId = externalId;
        this.status = EnrollmentStatus.ACTIVE;
        this.source = source;
        this.sourceVerificationId = sourceVerificationId;
        this.faceId = faceId;
        this.collectionId = collectionId;
        this.activeMarker = activeMarker(organizationId, externalId);
        this.createdAt = createdAt;
    }

    public void retire(Instant endedAt) {
        this.status = EnrollmentStatus.RETIRED;
        this.activeMarker = null;
        this.endedAt = endedAt;
    }

    public void revoke(Instant endedAt) {
        this.status = EnrollmentStatus.REVOKED;
        this.activeMarker = null;
        this.endedAt = endedAt;
    }

    public static String activeMarker(UUID organizationId, String externalId) {
        return organizationId + ":" + externalId;
    }
}
