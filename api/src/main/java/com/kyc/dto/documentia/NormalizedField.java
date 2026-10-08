package com.kyc.dto.documentia;

public record NormalizedField(
        String field,
        String value,
        String normalizedValue,
        String comparisonKey,
        Double confidence,
        String source,
        String validationStatus) {}
