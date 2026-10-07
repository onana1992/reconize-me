package com.kyc.dto.documentia;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record ParsedDocument(
        boolean documentDetected,
        String detection,
        Quality quality,
        Classification classification,
        String rawText,
        List<Zone> zones,
        List<ExtractedField> fields,
        List<String> indicators) {

    public record Quality(
            boolean readable,
            boolean blur,
            boolean glare,
            boolean cropped,
            boolean partiallyVisible,
            boolean multipleDocuments,
            boolean tooSmall,
            boolean lowLight,
            boolean overexposed,
            boolean tilted,
            boolean unreadable) {}

    public record Classification(
            String code,
            String country,
            String issuingJurisdiction,
            String documentType,
            String version,
            String side,
            Double confidence) {}

    public record Zone(
            String id,
            String text,
            Double confidence,
            BoundingBox boundingBox,
            @JsonInclude(JsonInclude.Include.NON_NULL) String bboxPrecision,
            @JsonInclude(JsonInclude.Include.NON_NULL) String bboxSource) {}

    public record BoundingBox(double x, double y, double width, double height) {}

    public record ExtractedField(String field, String value, Double confidence, String source) {}
}
