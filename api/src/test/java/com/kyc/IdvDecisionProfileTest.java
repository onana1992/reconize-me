package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kyc.enums.VerificationDecision;
import com.kyc.ports.BiometricAiPort.BiometricSignals;
import com.kyc.services.IdvDecisionEngine;
import com.kyc.services.IdvDecisionEngine.DecisionProfile;
import com.kyc.services.IdvDecisionEngine.VisionFacts;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IdvDecisionProfileTest {

    private final IdvDecisionEngine engine = new IdvDecisionEngine();

    @Test
    void expiredCanStayInReview() {
        DecisionProfile profile = new DecisionProfile(VerificationDecision.REVIEW, 0.70, 0.80, 0.75, 0.90);
        var verdict = engine.decideVision(null, clean(true), profile, false);
        assertEquals("REVIEW", verdict.issue());
        assertEquals(VerificationDecision.REVIEW, verdict.result().decision());
    }

    @Test
    void knownDocumentOutsideTheCorridorIsDeclined() {
        var verdict = engine.decideVision(null, clean(false), DecisionProfile.platform(), true);
        assertEquals("REJECT", verdict.issue());
        assertEquals(VerificationDecision.DECLINED, verdict.result().decision());
        assertEquals("unsupported_document", verdict.result().reasons().get(0));
    }

    @Test
    void raisedFaceApprovalSendsAPassingScoreToReview() {
        DecisionProfile profile = new DecisionProfile(VerificationDecision.DECLINED, 0.70, 0.80, 0.75, 0.95);
        var approved = new IdvDecisionEngine.Result(VerificationDecision.APPROVED, java.util.List.of(), java.util.List.of(), Map.of());
        var result = engine.applyFace(approved, new BiometricSignals(true, true, 0.91, 0.91), profile);
        assertEquals(VerificationDecision.REVIEW, result.decision());
        assertEquals("face_match_borderline", result.reasons().get(0));
    }

    private static VisionFacts clean(boolean expired) {
        return new VisionFacts(true, null, expired, false, false, false, false, false, false, "HIGH_CONFIDENCE", false, false, Map.of());
    }
}
