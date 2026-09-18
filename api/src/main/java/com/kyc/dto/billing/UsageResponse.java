package com.kyc.dto.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record UsageResponse(
        String currency,
        @JsonProperty("balance_minor") long balanceMinor,
        List<BillingResponse.ProductUsage> usage) {}
