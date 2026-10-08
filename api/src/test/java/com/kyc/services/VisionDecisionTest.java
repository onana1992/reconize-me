package com.kyc.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kyc.enums.VerificationDecision;
import com.kyc.ports.DocumentAiPort.DocumentSignals;
import com.kyc.services.IdvDecisionEngine.VisionFacts;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VisionDecisionTest {

    private final IdvDecisionEngine engine = new IdvDecisionEngine();

    @Test
    void highConfidenceWithoutFindingsIsApproved() {
        var verdict = engine.decideVision(null, facts(true, null, false, false, false, false, false, false, "HIGH_CONFIDENCE", false, false));

        assertEquals("PASS", verdict.issue());
        assertEquals(VerificationDecision.APPROVED, verdict.result().decision());
        assertTrue(verdict.result().reasons().isEmpty());
        assertTrue(verdict.result().signals().stream().noneMatch(signal -> "ocr_analyze_id".equals(signal.code())));
    }

    @Test
    void expiredDocumentIsDeclinedBeforeTheBand() {
        DocumentSignals textract = new DocumentSignals(
                "driving_license",
                "CA",
                "QC",
                true,
                true,
                false,
                "MARIE",
                "TREMBLAY",
                LocalDate.parse("1990-04-12"),
                "T1",
                LocalDate.parse("2020-01-15"),
                "textract_analyze_id");
        var verdict = engine.decideVision(
                textract, facts(true, null, true, true, false, false, false, false, "HIGH_CONFIDENCE", true, false));

        assertEquals("REJECT", verdict.issue());
        assertEquals(VerificationDecision.DECLINED, verdict.result().decision());
        assertEquals("document_expired", verdict.result().reasons().get(0));
        assertTrue(verdict.result().reasons().contains("DOCUMENT_UNKNOWN"));
        assertTrue(verdict.result().signals().stream().noneMatch(signal -> "ocr_analyze_id".equals(signal.code())));
    }

    @Test
    void unknownCodeIsReview() {
        var verdict = engine.decideVision(null, facts(true, null, false, true, false, false, false, false, "REVIEW", false, false));

        assertEquals("REVIEW", verdict.issue());
        assertEquals(VerificationDecision.REVIEW, verdict.result().decision());
        assertEquals(ListOf("DOCUMENT_UNKNOWN"), verdict.result().reasons());
    }

    @Test
    void suspiciousIndicatorIsReviewNotReject() {
        var verdict = engine.decideVision(
                null, facts(true, null, false, false, false, false, false, true, "HIGH_CONFIDENCE", false, true));

        assertEquals("REVIEW", verdict.issue());
        assertEquals(VerificationDecision.REVIEW, verdict.result().decision());
        assertEquals("SUSPICIOUS_INDICATOR", verdict.result().reasons().get(0));
    }

    @Test
    void unreadableImageIsRecapture() {
        var verdict = engine.decideVision(null, facts(false, "TOO_SMALL", true, false, false, false, false, false, "REVIEW", true, false));

        assertEquals("RECAPTURE", verdict.issue());
        assertNull(verdict.result().decision());
        assertEquals("TOO_SMALL", verdict.result().reasons().get(0));
        assertTrue(verdict.result().reasons().contains("document_expired"));
    }

    @Test
    void providerFailureStaysReview() {
        var result = engine.review("invalid_model_json");

        assertEquals(VerificationDecision.REVIEW, result.decision());
        assertEquals("invalid_model_json", result.reasons().get(0));
        assertTrue(result.signals().stream().noneMatch(signal -> "ocr_analyze_id".equals(signal.code())));
    }

    private static java.util.List<String> ListOf(String value) {
        return java.util.List.of(value);
    }

    private static VisionFacts facts(
            boolean readable,
            String capture,
            boolean expired,
            boolean unknown,
            boolean missing,
            boolean mrzFailed,
            boolean lowOcr,
            boolean suspicious,
            String band,
            boolean error,
            boolean warning) {
        return new VisionFacts(
                readable, capture, expired, unknown, missing, mrzFailed, lowOcr, suspicious, band, error, warning, Map.of());
    }
}
