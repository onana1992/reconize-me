package com.kyc.services.documentia;

import com.kyc.dto.documentia.DocumentImageQuality;
import com.kyc.dto.documentia.DocumentScores;
import com.kyc.dto.documentia.NormalizedField;
import com.kyc.dto.documentia.ParsedDocument;
import com.kyc.dto.documentia.ValidationIssue;
import com.kyc.services.documentia.SchemaEdition.FieldDef;
import java.util.List;

/** Formules du cahier §15. Le poids de la MRZ sort de la somme quand le format est NONE. */
public final class ConfidenceScorer {

    private ConfidenceScorer() {}

    public static DocumentScores score(
            SchemaEdition edition,
            ParsedDocument document,
            DocumentImageQuality quality,
            List<NormalizedField> fields,
            List<ValidationIssue> validation,
            Double mrzScore) {
        double documentTypeScore = documentType(document);
        double qualityScore = quality(quality);
        double ocrScore = ocr(edition, fields);
        double fieldScore = fields(edition, fields);
        double validationScore = validation(validation);
        boolean withMrz = mrzScore != null;
        double fieldWeight = withMrz ? 0.25 : 0.35;
        double validationWeight = withMrz ? 0.10 : 0.15;
        double overall = 0.15 * documentTypeScore
                + 0.20 * qualityScore
                + 0.15 * ocrScore
                + fieldWeight * fieldScore
                + (withMrz ? 0.15 * mrzScore : 0)
                + validationWeight * validationScore;
        double overallScore = unit(overall);
        return new DocumentScores(
                unit(documentTypeScore),
                unit(qualityScore),
                unit(ocrScore),
                unit(fieldScore),
                mrzScore == null ? null : unit(mrzScore),
                unit(validationScore),
                overallScore,
                band(overallScore));
    }

    private static double documentType(ParsedDocument document) {
        if (document == null || document.classification() == null) {
            return 0;
        }
        String code = document.classification().code();
        Double confidence = document.classification().confidence();
        if (code == null || "UNKNOWN".equals(code) || confidence == null) {
            return 0;
        }
        return confidence;
    }

    private static double quality(DocumentImageQuality quality) {
        if (quality == null) {
            return 1;
        }
        if (!quality.readable()) {
            return 0;
        }
        int severe = 0;
        if (quality.blur()) {
            severe++;
        }
        if (quality.glare()) {
            severe++;
        }
        if (quality.cropped()) {
            severe++;
        }
        if (quality.partiallyVisible()) {
            severe++;
        }
        if (quality.multipleDocuments()) {
            severe++;
        }
        if (quality.tooSmall()) {
            severe++;
        }
        if (quality.lowLight()) {
            severe++;
        }
        if (quality.overexposed()) {
            severe++;
        }
        return Math.max(0, 1 - 0.25 * severe);
    }

    private static double ocr(SchemaEdition edition, List<NormalizedField> fields) {
        if (edition == null) {
            return 0;
        }
        double sum = 0;
        int count = 0;
        for (FieldDef definition : edition.fields()) {
            if (!definition.required()) {
                continue;
            }
            NormalizedField field = find(fields, definition.name());
            if (field == null || "MISSING".equals(field.validationStatus()) || field.confidence() == null) {
                continue;
            }
            sum += field.confidence();
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private static double fields(SchemaEdition edition, List<NormalizedField> fields) {
        if (edition == null) {
            return 0;
        }
        int required = 0;
        int valid = 0;
        for (FieldDef definition : edition.fields()) {
            if (!definition.required()) {
                continue;
            }
            required++;
            NormalizedField field = find(fields, definition.name());
            if (field != null && "VALID".equals(field.validationStatus())) {
                valid++;
            }
        }
        return required == 0 ? 1 : (double) valid / required;
    }

    private static double validation(List<ValidationIssue> validation) {
        if (validation == null || validation.isEmpty()) {
            return 1;
        }
        double score = 1;
        for (ValidationIssue issue : validation) {
            if ("ERROR".equals(issue.severity())) {
                score -= 0.35;
            } else if ("WARNING".equals(issue.severity())) {
                score -= 0.15;
            }
        }
        return Math.max(0, score);
    }

    private static NormalizedField find(List<NormalizedField> fields, String name) {
        if (fields == null) {
            return null;
        }
        for (NormalizedField field : fields) {
            if (name.equals(field.field())) {
                return field;
            }
        }
        return null;
    }

    static String band(double overall) {
        if (overall >= 0.90) {
            return "HIGH_CONFIDENCE";
        }
        if (overall >= 0.75) {
            return "MEDIUM_CONFIDENCE";
        }
        if (overall >= 0.55) {
            return "LOW_CONFIDENCE";
        }
        return "REVIEW";
    }

    private static double unit(double value) {
        double rounded = Math.round(value * 10000.0) / 10000.0;
        if (rounded < 0) {
            return 0;
        }
        if (rounded > 1) {
            return 1;
        }
        return rounded;
    }
}
