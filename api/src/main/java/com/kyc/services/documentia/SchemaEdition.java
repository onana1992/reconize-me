package com.kyc.services.documentia;

import com.kyc.enums.FieldValueType;
import com.kyc.enums.MrzFormat;
import com.kyc.enums.RuleLevel;
import com.kyc.enums.RuleSeverity;
import java.util.List;
import java.util.UUID;

public record SchemaEdition(
        UUID versionId,
        String code,
        String country,
        String documentType,
        String version,
        String side,
        String issuingJurisdiction,
        List<FieldDef> fields,
        List<RuleDef> rules,
        MrzFormat mrzFormat) {

    public record FieldDef(
            String name, FieldValueType type, boolean required, String normalizer, String formats) {}

    public record RuleDef(RuleLevel level, String code, String expression, RuleSeverity severity) {}
}
