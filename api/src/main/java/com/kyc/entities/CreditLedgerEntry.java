package com.kyc.entities;

import com.kyc.enums.LedgerEntryType;
import com.kyc.enums.LedgerResourceType;
import com.kyc.enums.ProductCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "credit_ledger_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditLedgerEntry {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 16)
    private LedgerEntryType entryType;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "balance_after_minor", nullable = false)
    private long balanceAfterMinor;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ProductCode product;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 32)
    private LedgerResourceType resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "stripe_event_id", length = 64)
    private String stripeEventId;

    @Column(name = "stripe_checkout_session_id", length = 128)
    private String stripeCheckoutSessionId;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public CreditLedgerEntry(
            UUID id,
            UUID organizationId,
            LedgerEntryType entryType,
            long amountMinor,
            long balanceAfterMinor,
            ProductCode product,
            LedgerResourceType resourceType,
            UUID resourceId,
            String stripeEventId,
            String stripeCheckoutSessionId,
            UUID createdByUserId,
            Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.entryType = entryType;
        this.amountMinor = amountMinor;
        this.balanceAfterMinor = balanceAfterMinor;
        this.product = product;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.stripeEventId = stripeEventId;
        this.stripeCheckoutSessionId = stripeCheckoutSessionId;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
    }
}
