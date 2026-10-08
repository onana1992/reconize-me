package com.kyc.services.documentia;

import com.kyc.dto.documentia.DocumentIaCatalogResponse.FieldPrompt;
import com.kyc.dto.documentia.DocumentIaCatalogResponse.Schema;
import java.util.List;

public final class VisionPrompt {

    private VisionPrompt() {}

    public static String fromCatalog(List<Schema> schemas) {
        return fromCatalog(schemas, null);
    }

    public static String fromCatalog(List<Schema> schemas, String captureSide) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(
                "Return one JSON document matching the response schema. classification.version is only the version token, such as 2024. Do not copy country, document type, side, or jurisdiction into version. Field names must be copied from the list of the chosen side:\n");
        if (schemas != null) {
            for (Schema schema : schemas) {
                prompt.append("code: ").append(schema.code()).append('\n');
                prompt.append("country: ").append(schema.country()).append('\n');
                prompt.append("documentType: ").append(schema.documentType()).append('\n');
                prompt.append("side: ").append(schema.side()).append('\n');
                prompt.append("version: ").append(schema.version()).append('\n');
                if (schema.issuingJurisdiction() != null && !schema.issuingJurisdiction().isBlank()) {
                    prompt.append("issuingJurisdiction: ").append(schema.issuingJurisdiction()).append('\n');
                }
                if (schema.fields() != null) {
                    prompt.append("fields:\n");
                    for (FieldPrompt field : schema.fields()) {
                        prompt.append("  ")
                                .append(field.name())
                                .append(field.required() ? " required" : " optional");
                        if (field.hint() != null && !field.hint().isBlank()) {
                            prompt.append(". ").append(field.hint());
                        }
                        prompt.append('\n');
                    }
                }
            }
        }
        if (captureSide != null && !captureSide.isBlank()) {
            prompt.append("\nCapture side: ").append(captureSide.trim()).append('\n');
            prompt.append("Use only the field names listed for this side.\n");
        }
        return prompt.toString();
    }
}
