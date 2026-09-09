package com.kyc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "membership_invites",
        uniqueConstraints = @UniqueConstraint(name = "uq_invites_pending", columnNames = {"organization_id", "pending_key"}))
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

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "pending_key")
    private String pendingKey;

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
        this.pendingKey = email;
        this.createdAt = createdAt;
    }

    public boolean pending(Instant now) {
        return acceptedAt == null && cancelledAt == null && now.isBefore(expiresAt);
    }

    public boolean open() {
        return acceptedAt == null && cancelledAt == null;
    }

    public void accept(Instant at) {
        this.acceptedAt = at;
        this.pendingKey = null;
    }

    public void cancel(Instant at, String burnedTokenHash) {
        this.cancelledAt = at;
        this.pendingKey = null;
        this.tokenHash = burnedTokenHash;
        this.expiresAt = at;
    }

    public void rotate(String newTokenHash, Instant newExpiresAt) {
        rotate(newTokenHash, newExpiresAt, this.role);
    }

    public void rotate(String newTokenHash, Instant newExpiresAt, String role) {
        this.tokenHash = newTokenHash;
        this.expiresAt = newExpiresAt;
        this.role = role;
    }
}
