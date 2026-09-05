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
@Table(name = "membership_invites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipInvite {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public MembershipInvite(
            UUID id,
            UUID organizationId,
            String email,
            String role,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.email = email;
        this.role = role;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public boolean pending(Instant now) {
        return acceptedAt == null && now.isBefore(expiresAt);
    }

    public void accept(Instant at) {
        this.acceptedAt = at;
    }
}
