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
@Table(name = "api_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiKey {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    public ApiKey(UUID id, UUID organizationId, String keyPrefix, String keyHash, Instant createdAt) {
        this(id, organizationId, keyPrefix, keyHash, createdAt, null);
    }

    public ApiKey(
            UUID id, UUID organizationId, String keyPrefix, String keyHash, Instant createdAt, UUID createdByUserId) {
        this.id = id;
        this.organizationId = organizationId;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.revoked = false;
        this.createdAt = createdAt;
        this.createdByUserId = createdByUserId;
    }

    public void revoke() {
        this.revoked = true;
    }
}
