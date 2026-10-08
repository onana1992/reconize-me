package com.kyc.services.documentia;

import com.kyc.dto.documentia.MrzReport;
import com.kyc.dto.documentia.NormalizedField;
import com.kyc.dto.documentia.ParsedDocument;
import com.kyc.dto.documentia.ValidationIssue;
import com.kyc.ports.DocumentAiPort.DocumentSignals;
import com.kyc.services.IdvDecisionEngine;
import com.kyc.services.IdvDecisionEngine.VisionFacts;
import com.kyc.services.documentia.SchemaEdition.FieldDef;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Indicateurs L6 et faits remis au moteur de règles. L4 est déjà dans la validation MRZ. */
public final class DocumentValidator {

    private DocumentValidator() {}

    public record Prepared(
            List<ValidationIssue> validation,
            List<String> indicators,
            VisionFacts facts,
            DocumentSignals signals) {}

    public static Prepared prepare(
            SchemaEdition edition,
            ParsedDocument document,
            List<NormalizedField> fields,
            List<ValidationIssue> validation,
            MrzReport mrz,
            boolean readable,
            String captureReason) {
        List<ValidationIssue> issues = new ArrayList<>(validation == null ? List.of() : validation);
        List<String> indicators = new ArrayList<>();
        if (document != null && document.indicators() != null) {
            for (String code : document.indicators()) {
                if (DocumentAnalysisParser.MODEL_INDICATORS.contains(code) && !contains(issues, code)) {
                    issues.add(new ValidationIssue("L6", code, "WARNING", null));
                }
                add(indicators, code);
            }
        }
        boolean unknown = unknown(document);
        if (unknown) {
            add(indicators, "DOCUMENT_UNKNOWN");
        }
        for (ValidationIssue issue : issues) {
            add(indicators, issue.code());
        }
        boolean expired = contains(issues, "document_expired");
        boolean missing = missing(fields);
        boolean mrzFailed = mrzFailed(mrz);
        boolean lowOcr = lowOcr(edition, fields);
        boolean suspicious = suspicious(issues);
        boolean hasError = issues.stream().anyMatch(issue -> "ERROR".equals(issue.severity()));
        boolean hasWarning = issues.stream().anyMatch(issue -> "WARNING".equals(issue.severity()));
        Map<String, Object> identity = document == null || document.extractedIdentity() == null
                ? Map.of()
                : document.extractedIdentity();
        VisionFacts facts = new VisionFacts(
                readable,
                captureReason,
                expired,
                unknown,
                missing,
                mrzFailed,
                lowOcr,
                suspicious,
                null,
                hasError,
                hasWarning,
                identity);
        return new Prepared(List.copyOf(issues), List.copyOf(indicators), facts, project(edition, document, fields, expired, mrz));
    }

    private static DocumentSignals project(
            SchemaEdition edition,
            ParsedDocument document,
            List<NormalizedField> fields,
            boolean expired,
            MrzReport mrz) {
        String code = document == null || document.classification() == null
                ? "UNKNOWN"
                : document.classification().code();
        boolean supported = edition != null && !"UNKNOWN".equals(code);
        String type = edition == null ? "unknown" : edition.documentType().toLowerCase(java.util.Locale.ROOT);
        String country = edition == null ? "ZZ" : edition.country();
        boolean mrzAvailable = mrz != null && "VALID".equals(mrz.status());
        return new DocumentSignals(
                type,
                country,
                edition == null ? null : edition.issuingJurisdiction(),
                expired,
                supported,
                mrzAvailable,
                text(fields, "firstName"),
                text(fields, "lastName"),
                date(fields, "dateOfBirth"),
                text(fields, "documentNumber"),
                date(fields, "expirationDate"),
                "vision_llm");
    }

    private static boolean unknown(ParsedDocument document) {
        if (document == null) {
            return false;
        }
        if (document.classification() == null) {
            return true;
        }
        if ("UNKNOWN".equals(document.classification().code())) {
            return true;
        }
        if (document.indicators() != null && document.indicators().contains("DOCUMENT_UNKNOWN")) {
            return true;
        }
        Double confidence = document.classification().confidence();
        return confidence != null && confidence < DocumentAnalysisParser.LOW_CLASS_CONFIDENCE;
    }

    private static boolean missing(List<NormalizedField> fields) {
        if (fields == null) {
            return false;
        }
        for (NormalizedField field : fields) {
            if ("MISSING".equals(field.validationStatus())) {
                return true;
            }
        }
        return false;
    }

    private static boolean mrzFailed(MrzReport mrz) {
        if (mrz == null || mrz.status() == null) {
            return false;
        }
        return switch (mrz.status()) {
            case "FAILED", "MISMATCH", "UNAVAILABLE" -> true;
            default -> false;
        };
    }

    private static boolean lowOcr(SchemaEdition edition, List<NormalizedField> fields) {
        if (edition == null || fields == null) {
            return false;
        }
        for (FieldDef definition : edition.fields()) {
            if (!definition.required()) {
                continue;
            }
            for (NormalizedField field : fields) {
                if (definition.name().equals(field.field())
                        && field.confidence() != null
                        && field.confidence() < IdvDecisionEngine.LOW_OCR_CONFIDENCE) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean suspicious(List<ValidationIssue> issues) {
        for (ValidationIssue issue : issues) {
            if ("L6".equals(issue.level()) && "WARNING".equals(issue.severity())) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(List<ValidationIssue> issues, String code) {
        for (ValidationIssue issue : issues) {
            if (code.equals(issue.code())) {
                return true;
            }
        }
        return false;
    }

    private static void add(List<String> indicators, String code) {
        if (code != null && !indicators.contains(code)) {
            indicators.add(code);
        }
    }

    private static String text(List<NormalizedField> fields, String name) {
        if (fields == null) {
            return null;
        }
        for (NormalizedField field : fields) {
            if (name.equals(field.field()) && "VALID".equals(field.validationStatus())) {
                return field.normalizedValue();
            }
        }
        return null;
    }

    private static LocalDate date(List<NormalizedField> fields, String name) {
        String value = text(fields, name);
        return value == null ? null : LocalDate.parse(value);
    }
}
