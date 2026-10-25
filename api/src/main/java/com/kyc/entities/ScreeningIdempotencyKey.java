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
@Table(name = "screening_idempotency_keys")
@IdClass(ScreeningIdempotencyKey.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScreeningIdempotencyKey {

    @Id
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "screening_id", nullable = false)
    private UUID screeningId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ScreeningIdempotencyKey(
            UUID organizationId, String key, String requestHash, UUID screeningId, Instant createdAt) {
        this.organizationId = organizationId;
        this.key = key;
        this.requestHash = requestHash;
        this.screeningId = screeningId;
        this.createdAt = createdAt;
    }

    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Pk implements Serializable {
        private UUID organizationId;
        private String key;
    }
}
