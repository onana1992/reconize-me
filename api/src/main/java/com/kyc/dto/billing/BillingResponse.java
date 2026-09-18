package com.kyc.dto.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BillingResponse(
        String currency,
        @JsonProperty("balance_minor") long balanceMinor,
        @JsonProperty("unit_amount_minor") long unitAmountMinor,
        @JsonProperty("live_unlocked") boolean liveUnlocked,
        List<Long> packs,
        List<ProductUsage> usage,
        LedgerPage ledger) {

    public record ProductUsage(
            String product,
            @JsonProperty("sandbox_count") long sandboxCount,
            @JsonProperty("live_count") long liveCount,
            @JsonProperty("live_debit_minor") long liveDebitMinor) {}

    public record LedgerPage(List<LedgerEntryResponse> entries, @JsonProperty("next_cursor") String nextCursor) {}

    public record LedgerEntryResponse(
            UUID id,
            @JsonProperty("entry_type") String entryType,
            @JsonProperty("amount_minor") long amountMinor,
            @JsonProperty("balance_after_minor") long balanceAfterMinor,
            String product,
            @JsonProperty("resource_type") String resourceType,
            @JsonProperty("resource_id") UUID resourceId,
            @JsonProperty("created_at") Instant createdAt) {}
}
