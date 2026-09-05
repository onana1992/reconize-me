package com.kyc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKeyId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

    @Id
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Id
    @Column(name = "`key`", nullable = false, length = 64)
    private String keyValue;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "verification_id")
    private UUID verificationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public IdempotencyKey(
            UUID organizationId, String keyValue, String requestHash, UUID verificationId, Instant createdAt) {
        this.organizationId = organizationId;
        this.keyValue = keyValue;
        this.requestHash = requestHash;
        this.verificationId = verificationId;
        this.createdAt = createdAt;
    }
}
