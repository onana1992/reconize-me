package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kyc.enums.VerificationDecision;
import com.kyc.ports.BiometricAiPort.BiometricSignals;
import com.kyc.ports.DocumentAiPort.DocumentSignals;
import com.kyc.services.IdvDecisionEngine;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FaceMatchThresholdTest {

    private final IdvDecisionEngine engine = new IdvDecisionEngine();

    @Test
    void thresholds() {
        DocumentSignals doc = supportedDoc();
        assertEquals(VerificationDecision.DECLINED, engine.decideLive(doc, bio(0.74)).decision());
        assertEquals("face_match_fail", engine.decideLive(doc, bio(0.74)).reasons().get(0));
        assertEquals(VerificationDecision.REVIEW, engine.decideLive(doc, bio(0.80)).decision());
        assertEquals("face_match_borderline", engine.decideLive(doc, bio(0.80)).reasons().get(0));
        assertEquals(VerificationDecision.APPROVED, engine.decideLive(doc, bio(0.91)).decision());
        assertTrue(engine.decideLive(doc, bio(0.91)).extractedIdentity().containsKey("document_type"));
    }

    @Test
    void unsupportedSkipsBiometricsMeaning() {
        DocumentSignals unsupported =
                new DocumentSignals("unknown", "ZZ", null, false, false, false, null, null, null, null, null, "textract_analyze_id");
        var result = engine.decideLive(unsupported, null);
        assertEquals(VerificationDecision.DECLINED, result.decision());
        assertEquals("unsupported_document", result.reasons().get(0));
        assertEquals(Map.of(), result.extractedIdentity());
    }

    private static DocumentSignals supportedDoc() {
        return new DocumentSignals(
                "driving_license",
                "CA",
                "QC",
                false,
                true,
                false,
                "A",
                "B",
                LocalDate.of(1990, 1, 1),
                "T1",
                LocalDate.of(2030, 1, 1),
                "textract_analyze_id");
    }

    private static BiometricSignals bio(double score) {
        return new BiometricSignals(true, score >= 0.90, 1.0, score);
    }
}
