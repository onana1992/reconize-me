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
        name = "integrations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "product", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Integration {

    public static final String PRODUCT_IDENTITY = "identity";
    public static final String MODE_TEST = "test";
    public static final String MODE_LIVE = "live";

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 32)
    private String product;

    @Column(nullable = false, length = 8)
    private String mode;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Integration(UUID id, UUID organizationId, String product, String mode, String name, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.product = product;
        this.mode = mode;
        this.name = name;
        this.createdAt = createdAt;
    }

    public boolean isLive() {
        return MODE_LIVE.equals(mode);
    }

    public boolean isTest() {
        return MODE_TEST.equals(mode);
    }
}
