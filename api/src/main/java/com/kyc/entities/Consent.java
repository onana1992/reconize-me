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
@Table(name = "consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consent {

    @Id
    private UUID id;

    @Column(name = "verification_id", nullable = false)
    private UUID verificationId;

    @Column(nullable = false, length = 16)
    private String decision;

    @Column(name = "text_version", nullable = false, length = 64)
    private String textVersion;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    public Consent(
            UUID id,
            UUID verificationId,
            String decision,
            String textVersion,
            Instant acceptedAt,
            String ipHash,
            String userAgent) {
        this.id = id;
        this.verificationId = verificationId;
        this.decision = decision;
        this.textVersion = textVersion;
        this.acceptedAt = acceptedAt;
        this.ipHash = ipHash;
        this.userAgent = userAgent;
    }
}
