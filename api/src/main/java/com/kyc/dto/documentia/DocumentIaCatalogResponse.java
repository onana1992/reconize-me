package com.kyc.dto.documentia;

import java.util.List;
import java.util.UUID;

public record DocumentIaCatalogResponse(List<Schema> schemas) {

    public record FieldPrompt(String name, boolean required, String hint) {}

    public record Schema(
            UUID schemaVersionId,
            String code,
            String country,
            String documentType,
            String side,
            String version,
            String issuingJurisdiction,
            List<FieldPrompt> fields) {}
}
