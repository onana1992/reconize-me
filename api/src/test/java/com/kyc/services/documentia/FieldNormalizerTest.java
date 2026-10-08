package com.kyc.services.documentia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kyc.dto.documentia.NormalizedField;
import com.kyc.dto.documentia.ParsedDocument;
import com.kyc.dto.documentia.ParsedDocument.Classification;
import com.kyc.dto.documentia.ParsedDocument.ExtractedField;
import com.kyc.dto.documentia.ValidationIssue;
import com.kyc.enums.FieldValueType;
import com.kyc.enums.MrzFormat;
import com.kyc.enums.RuleLevel;
import com.kyc.enums.RuleSeverity;
import com.kyc.services.documentia.SchemaEdition.FieldDef;
import com.kyc.services.documentia.SchemaEdition.RuleDef;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FieldNormalizerTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-07");
    private static final String DATES = "yyyy-MM-dd,yyyy/MM/dd,dd/MM/yyyy,MM/dd/yyyy";

    @Test
    void nameKeepsTheRawValueAndBuildsAComparisonKey() {
        FieldNormalizer.Result result = FieldNormalizer.normalize(
                edition(List.of(name("lastName", true)), List.of()),
                document(List.of(new ExtractedField("lastName", "  \u00C9lodie   Marie ", 0.9, "VISUAL_TEXT"))),
                TODAY);

        NormalizedField field = result.fields().get(0);
        assertEquals("  \u00C9lodie   Marie ", field.value());
        assertEquals("\u00C9lodie Marie", field.normalizedValue());
        assertEquals("ELODIE MARIE", field.comparisonKey());
        assertEquals("VALID", field.validationStatus());
        assertEquals("\u00C9lodie Marie", result.extractedIdentity().get("last_name"));
    }

    @Test
    void ambiguousDateKeepsTheRawValue() {
        FieldNormalizer.Result result = FieldNormalizer.normalize(
                edition(List.of(date("dateOfBirth", true)), List.of()),
                document(List.of(new ExtractedField("dateOfBirth", "12/05/1990", null, "VISUAL_TEXT"))),
                TODAY);

        NormalizedField field = result.fields().get(0);
        assertEquals("12/05/1990", field.value());
        assertNull(field.normalizedValue());
        assertEquals("AMBIGUOUS", field.validationStatus());
        assertEquals("ambiguous_date", result.validation().get(0).code());
    }

    @Test
    void impossibleDayIsInvalid() {
        FieldNormalizer.Result result = FieldNormalizer.normalize(
                edition(List.of(date("dateOfBirth", true)), List.of()),
                document(List.of(new ExtractedField("dateOfBirth", "31/02/1990", null, "VISUAL_TEXT"))),
                TODAY);

        assertEquals("INVALID", result.fields().get(0).validationStatus());
        assertEquals("impossible_date", result.validation().get(0).code());
        assertEquals("31/02/1990", result.fields().get(0).value());
    }

    @Test
    void missingRequiredFieldIsAnObject() {
        FieldNormalizer.Result result = FieldNormalizer.normalize(
                edition(List.of(name("lastName", true)), List.of()), document(List.of()), TODAY);

        assertEquals("MISSING", result.fields().get(0).validationStatus());
        assertNull(result.fields().get(0).value());
        assertEquals("missing_expected_field", result.validation().get(0).code());
        assertEquals("L2", result.validation().get(0).level());
    }

    @Test
    void birthAfterIssueAddsTheRuleCode() {
        FieldNormalizer.Result result = FieldNormalizer.normalize(
                edition(
                        List.of(date("dateOfBirth", true), date("dateOfIssue", true)),
                        List.of(new RuleDef(
                                RuleLevel.L3, "DOB_AFTER_ISSUE", "dateOfBirth > dateOfIssue", RuleSeverity.ERROR))),
                document(List.of(
                        new ExtractedField("dateOfBirth", "2020-06-02", null, "VISUAL_TEXT"),
                        new ExtractedField("dateOfIssue", "2020-06-01", null, "VISUAL_TEXT"))),
                TODAY);

        assertTrue(result.validation().stream().anyMatch(issue -> "DOB_AFTER_ISSUE".equals(issue.code())
                && "L3".equals(issue.level())
                && "ERROR".equals(issue.severity())));
    }

    @Test
    void expirationBeforeTodayIsAnError() {
        FieldNormalizer.Result result = FieldNormalizer.normalize(
                edition(List.of(date("expirationDate", true)), List.of()),
                document(List.of(new ExtractedField("expirationDate", "2020-01-01", null, "VISUAL_TEXT"))),
                TODAY);

        ValidationIssue issue = result.validation().get(0);
        assertEquals("document_expired", issue.code());
        assertEquals("L3", issue.level());
        assertEquals("VALID", result.fields().get(0).validationStatus());
        assertEquals("2020-01-01", result.fields().get(0).normalizedValue());
    }

    private static SchemaEdition edition(List<FieldDef> fields, List<RuleDef> rules) {
        return new SchemaEdition(
                UUID.randomUUID(),
                "QUEBEC_DRIVER_LICENSE",
                "CA",
                "DRIVING_LICENSE",
                "2024",
                "FRONT",
                "QC",
                fields,
                rules,
                MrzFormat.NONE);
    }

    private static FieldDef name(String name, boolean required) {
        return new FieldDef(name, FieldValueType.NAME, required, "ICAO_NAME", null);
    }

    private static FieldDef date(String name, boolean required) {
        return new FieldDef(name, FieldValueType.DATE, required, "ISO_DATE", DATES);
    }

    private static ParsedDocument document(List<ExtractedField> fields) {
        return new ParsedDocument(
                true,
                "DOCUMENT_PRESENT",
                null,
                new Classification("QUEBEC_DRIVER_LICENSE", "CA", "QC", "DRIVING_LICENSE", "2024", "FRONT", 0.9),
                null,
                List.of(),
                fields,
                List.of(),
                null);
    }
}
