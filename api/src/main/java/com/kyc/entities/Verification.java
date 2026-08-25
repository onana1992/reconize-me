package com.kyc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verifications")
public class Verification {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "external_id")
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VerificationStatus status;

    @Column(name = "applicant_first_name")
    private String applicantFirstName;

    @Column(name = "applicant_last_name")
    private String applicantLastName;

    @Column(name = "applicant_email")
    private String applicantEmail;

    @Column(name = "hosted_token_hash", nullable = false, unique = true, length = 64)
    private String hostedTokenHash;

    @Column(name = "hosted_expires_at", nullable = false)
    private Instant hostedExpiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Verification() {
    }

    public Verification(
            UUID id,
            UUID organizationId,
            String externalId,
            VerificationStatus status,
            String hostedTokenHash,
            Instant hostedExpiresAt,
            Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.externalId = externalId;
        this.status = status;
        this.hostedTokenHash = hostedTokenHash;
        this.hostedExpiresAt = hostedExpiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getExternalId() {
        return externalId;
    }

    public VerificationStatus getStatus() {
        return status;
    }

    public String getApplicantFirstName() {
        return applicantFirstName;
    }

    public String getApplicantLastName() {
        return applicantLastName;
    }

    public String getApplicantEmail() {
        return applicantEmail;
    }

    public String getHostedTokenHash() {
        return hostedTokenHash;
    }

    public Instant getHostedExpiresAt() {
        return hostedExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setApplicant(String firstName, String lastName, String email) {
        this.applicantFirstName = firstName;
        this.applicantLastName = lastName;
        this.applicantEmail = email;
    }

    public void transitionTo(VerificationStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
    }
}
