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
@Table(name = "email_verification_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "invite_id")
    private UUID inviteId;

    public EmailVerificationToken(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
        this(id, userId, tokenHash, expiresAt, null);
    }

    public EmailVerificationToken(UUID id, UUID userId, String tokenHash, Instant expiresAt, UUID inviteId) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.inviteId = inviteId;
    }

    public boolean usable(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    public void consume(Instant at) {
        this.consumedAt = at;
    }
}
