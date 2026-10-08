package com.kyc.services.documentia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.kyc.dto.documentia.DocumentImageQuality;
import com.kyc.dto.documentia.DocumentScores;
import com.kyc.dto.documentia.NormalizedField;
import com.kyc.dto.documentia.ParsedDocument;
import com.kyc.dto.documentia.ParsedDocument.Classification;
import com.kyc.dto.documentia.ValidationIssue;
import com.kyc.enums.FieldValueType;
import com.kyc.enums.MrzFormat;
import com.kyc.services.documentia.SchemaEdition.FieldDef;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfidenceScorerTest {

    @Test
    void withoutMrzTheWeightMovesToFieldsAndValidation() {
        DocumentScores scores = ConfidenceScorer.score(edition(), present("QUEBEC_DRIVER_LICENSE", 1.0), null, validFields(1.0), List.of(), null);

        assertNull(scores.mrzScore());
        assertEquals(1.0, scores.overallScore());
        assertEquals("HIGH_CONFIDENCE", scores.band());
    }

    @Test
    void withMrzKeepsTheFifteenPercentWeight() {
        DocumentScores scores = ConfidenceScorer.score(edition(), present("PASSPORT_TD3", 1.0), null, validFields(1.0), List.of(), 0.4);

        assertEquals(0.4, scores.mrzScore());
        assertEquals(0.91, scores.overallScore());
        assertEquals("HIGH_CONFIDENCE", scores.band());
    }

    @Test
    void unknownCodeZerosTheTypeScore() {
        DocumentScores scores = ConfidenceScorer.score(null, present("UNKNOWN", 0.99), null, List.of(), List.of(), null);

        assertEquals(0.0, scores.documentTypeScore());
        assertEquals(0.35, scores.overallScore());
        assertEquals("REVIEW", scores.band());
    }

    @Test
    void unreadableImageZerosQuality() {
        DocumentImageQuality quality = new DocumentImageQuality(
                false, true, false, false, false, false, true, false, false, false, true, "TOO_SMALL");
        DocumentScores scores = ConfidenceScorer.score(edition(), present("QUEBEC_DRIVER_LICENSE", 0.94), quality, validFields(0.95), List.of(), null);

        assertEquals(0.0, scores.qualityScore());
    }

    @Test
    void errorsAndWarningsFloorAtZero() {
        List<ValidationIssue> issues = List.of(
                new ValidationIssue("L1", "impossible_date", "ERROR", "dateOfBirth"),
                new ValidationIssue("L1", "impossible_date", "ERROR", "expirationDate"),
                new ValidationIssue("L3", "document_expired", "ERROR", "expirationDate"),
                new ValidationIssue("L2", "missing_expected_field", "WARNING", "lastName"));
        DocumentScores scores = ConfidenceScorer.score(edition(), present("QUEBEC_DRIVER_LICENSE", 0.94), null, validFields(0.95), issues, null);

        assertEquals(0.0, scores.validationScore());
    }

    private static SchemaEdition edition() {
        return new SchemaEdition(
                UUID.randomUUID(),
                "QUEBEC_DRIVER_LICENSE",
                "CA",
                "DRIVING_LICENSE",
                "2024",
                "FRONT",
                "QC",
                List.of(
                        new FieldDef("firstName", FieldValueType.NAME, true, null, null),
                        new FieldDef("lastName", FieldValueType.NAME, true, null, null)),
                List.of(),
                MrzFormat.NONE);
    }

    private static ParsedDocument present(String code, double confidence) {
        return new ParsedDocument(
                true,
                "DOCUMENT_PRESENT",
                null,
                new Classification(code, "CA", "QC", "DRIVING_LICENSE", "2024", "FRONT", confidence),
                null,
                List.of(),
                List.of(),
                List.of(),
                null);
    }

    private static List<NormalizedField> validFields(double confidence) {
        return List.of(
                new NormalizedField("firstName", "MARIE", "MARIE", "MARIE", confidence, "VISUAL_TEXT", "VALID"),
                new NormalizedField("lastName", "TREMBLAY", "TREMBLAY", "TREMBLAY", confidence, "VISUAL_TEXT", "VALID"));
    }
}
