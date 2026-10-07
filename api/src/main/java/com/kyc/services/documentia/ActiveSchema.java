package com.kyc.services.documentia;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ActiveSchema(
        UUID versionId,
        String code,
        String country,
        String documentType,
        String version,
        List<String> sides,
        String issuingJurisdiction,
        Set<String> fieldNames) {

    public boolean allows(String side) {
        return side != null && sides.contains(side);
    }

    public String onlySide() {
        return sides.size() == 1 ? sides.get(0) : null;
    }
}
