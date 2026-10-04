package com.kyc.entities;

import com.kyc.enums.IntegrationMode;
import com.kyc.enums.ProductCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductCode product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private IntegrationMode mode;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Integration(
            UUID id, UUID organizationId, ProductCode product, IntegrationMode mode, String name, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.product = product;
        this.mode = mode;
        this.name = name;
        this.createdAt = createdAt;
    }

    public boolean isLive() {
        return mode == IntegrationMode.LIVE;
    }

    public boolean isTest() {
        return mode == IntegrationMode.TEST;
    }
}
