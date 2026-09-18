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
@Table(name = "stripe_customers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StripeCustomer {

    @Id
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "stripe_customer_id", nullable = false, unique = true, length = 128)
    private String stripeCustomerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public StripeCustomer(UUID organizationId, String stripeCustomerId, Instant createdAt) {
        this.organizationId = organizationId;
        this.stripeCustomerId = stripeCustomerId;
        this.createdAt = createdAt;
    }

    public void replaceCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }
}
