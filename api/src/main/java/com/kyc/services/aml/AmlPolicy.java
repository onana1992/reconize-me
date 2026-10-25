package com.kyc.services.aml;

import com.kyc.enums.AmlCategory;
import com.kyc.enums.AmlOnMatch;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public record AmlPolicy(
        String version,
        BigDecimal reviewThreshold,
        BigDecimal confirmThreshold,
        Set<AmlCategory> categories,
        boolean attachToIdentity,
        AmlOnMatch onMatch,
        UUID testIntegrationId,
        UUID liveIntegrationId) {

    public static AmlPolicy factory() {
        return new AmlPolicy(
                "aml-policy-0",
                new BigDecimal("0.700"),
                new BigDecimal("0.950"),
                EnumSet.allOf(AmlCategory.class),
                false,
                AmlOnMatch.REVIEW,
                null,
                null);
    }

    public boolean allows(AmlCategory category) {
        return categories.contains(category);
    }
}
