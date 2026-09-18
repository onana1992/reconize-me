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
@Table(name = "credit_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditAccount {

    @Id
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CreditAccount(UUID organizationId, String currency, long balanceMinor, Instant createdAt) {
        this.organizationId = organizationId;
        this.currency = currency;
        this.balanceMinor = balanceMinor;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void apply(long delta, Instant now) {
        this.balanceMinor += delta;
        this.updatedAt = now;
    }
}
