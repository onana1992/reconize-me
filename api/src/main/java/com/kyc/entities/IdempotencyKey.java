package com.kyc.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKey.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

    @Id
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "verification_id", nullable = false)
    private UUID verificationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public IdempotencyKey(UUID organizationId, String key, String requestHash, UUID verificationId, Instant createdAt) {
        this.organizationId = organizationId;
        this.key = key;
        this.requestHash = requestHash;
        this.verificationId = verificationId;
        this.createdAt = createdAt;
    }

    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Pk implements Serializable {
        private UUID organizationId;
        private String key;

        public Pk(UUID organizationId, String key) {
            this.organizationId = organizationId;
            this.key = key;
        }
    }
}
