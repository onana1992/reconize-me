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
                "Return one JSON document matching the response schema. First recognize which document this is and which side of it is shown. Compare the image with the reading of every entry below: each entry is one side of one document. Set classification.code and classification.side from the entry that matches. classification.side is required. classification.version is only the version token, such as 2024. Do not copy country, document type, side, or jurisdiction into version. Field names must be copied from the list of the chosen side:\n");
        if (schemas != null) {
            for (Schema schema : schemas) {
                if (otherSide(captureSide, schema)) {
                    appendBrief(prompt, schema);
                } else {
                    appendFull(prompt, schema);
                }
            }
        }
        if (captureSide != null && !captureSide.isBlank()) {
            prompt.append("\nCapture side: ").append(captureSide.trim()).append('\n');
            prompt.append(
                    "The applicant was asked for this side. Set classification.side to the side you actually see, even when it differs. Use the field list of the side you report. An entry for the other side only gives its code and a short reading: if that is what you see, report that side and return an empty fields array.\n");
        }
        return prompt.toString();
    }

    private static boolean otherSide(String captureSide, Schema schema) {
        return captureSide != null
                && !captureSide.isBlank()
                && schema.side() != null
                && !captureSide.trim().equalsIgnoreCase(schema.side());
    }

    private static void appendBrief(StringBuilder prompt, Schema schema) {
        prompt.append("code: ")
                .append(schema.code())
                .append(" side: ")
                .append(schema.side())
                .append(" version: ")
                .append(schema.version())
                .append('\n');
        String brief = firstSentence(schema.reading());
        if (brief != null) {
            prompt.append("reading: ").append(brief).append('\n');
        }
    }

    private static void appendFull(StringBuilder prompt, Schema schema) {
        prompt.append("code: ").append(schema.code()).append('\n');
        prompt.append("country: ").append(schema.country()).append('\n');
        prompt.append("documentType: ").append(schema.documentType()).append('\n');
        prompt.append("side: ").append(schema.side()).append('\n');
        prompt.append("version: ").append(schema.version()).append('\n');
        if (schema.issuingJurisdiction() != null && !schema.issuingJurisdiction().isBlank()) {
            prompt.append("issuingJurisdiction: ").append(schema.issuingJurisdiction()).append('\n');
        }
        if (schema.reading() != null && !schema.reading().isBlank()) {
            prompt.append("reading: ").append(schema.reading()).append('\n');
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

    private static String firstSentence(String reading) {
        if (reading == null || reading.isBlank()) {
            return null;
        }
        int dot = reading.indexOf('.');
        String sentence = (dot < 0 ? reading : reading.substring(0, dot + 1)).trim();
        if (sentence.length() > 180) {
            return sentence.substring(0, 177).trim() + "...";
        }
        return sentence;
    }
}
