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
@Table(name = "consents")
public class Consent {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "verification_id", nullable = false, unique = true)
    private UUID verificationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConsentDecision decision;

    @Column(name = "text_version", nullable = false, length = 32)
    private String textVersion;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Consent() {
    }

    public Consent(
            UUID id,
            UUID organizationId,
            UUID verificationId,
            ConsentDecision decision,
            String textVersion,
            Instant acceptedAt,
            String ipHash,
            String userAgent,
            Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.verificationId = verificationId;
        this.decision = decision;
        this.textVersion = textVersion;
        this.acceptedAt = acceptedAt;
        this.ipHash = ipHash;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVerificationId() {
        return verificationId;
    }

    public ConsentDecision getDecision() {
        return decision;
    }

    public String getIpHash() {
        return ipHash;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getTextVersion() {
        return textVersion;
    }
}
