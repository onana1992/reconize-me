package com.kyc.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kyc.enums.SignalOutcome;
import com.kyc.enums.VerificationDecision;
import com.kyc.services.IdvDecisionEngine.AlertScore;
import com.kyc.services.IdvDecisionEngine.AuthenticityFacts;
import com.kyc.services.IdvDecisionEngine.AuthenticityPolicy;
import com.kyc.services.IdvDecisionEngine.AuthenticityVerdict;
import com.kyc.services.IdvDecisionEngine.Signal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthenticityDecisionTest {

    private final IdvDecisionEngine engine = new IdvDecisionEngine();

    @Test
    void scoresUnderReviewPassAsAuthentic() {
        AuthenticityVerdict verdict = engine.applyAuthenticity(measured(1.0, 0.1, 0.1, 0.1, 0.1, 0.1), open());
        assertEquals("PASS", verdict.issue());
        assertEquals("auth-1", verdict.rulesVersion());
        assertTrue(verdict.reasons().isEmpty());
        assertEquals(List.of(new Signal("document_authentic", SignalOutcome.PASS, 1.0)), verdict.signals());
    }

    @Test
    void closedStrongAlertStaysReview() {
        AuthenticityVerdict verdict = engine.applyAuthenticity(measured(0.1, 0.2, 0.91, 0.62, 0.04, 0.03), open());
        assertEquals("REVIEW", verdict.issue());
        assertEquals(List.of("tampering_suspected"), verdict.reasons());
        assertEquals(
                List.of(
                        new Signal("text_alteration_suspected", SignalOutcome.FAIL, 0.91),
                        new Signal("compression_inconsistency", SignalOutcome.FAIL, 0.62)),
                verdict.signals());
    }

    @Test
    void openedStrongAlertRejectsOnTheHighest() {
        AuthenticityVerdict verdict = engine.applyAuthenticity(
                measured(0.09, 0.20, 0.91, 0.62, 0.04, 0.03),
                new AuthenticityPolicy(0.55, 0.80, Set.of("text_alteration")));
        assertEquals("REJECT", verdict.issue());
        assertEquals(List.of("text_alteration_suspected"), verdict.reasons());
        assertEquals("text_alteration_suspected", verdict.signals().get(0).code());
        assertEquals("compression_inconsistency", verdict.signals().get(1).code());
    }

    @Test
    void screenReplayRejectsOnlyWhenOpened() {
        AuthenticityFacts grid = measured(0.1, 0.0, 0.0, 0.0, 0.90, 0.0);
        AuthenticityVerdict closed = engine.applyAuthenticity(grid, open());
        assertEquals("REVIEW", closed.issue());
        assertEquals(List.of("tampering_suspected"), closed.reasons());
        assertEquals("screen_replay_detected", closed.signals().get(0).code());

        AuthenticityVerdict opened = engine.applyAuthenticity(
                grid, new AuthenticityPolicy(0.55, 0.80, Set.of("screen_replay")));
        assertEquals("REJECT", opened.issue());
        assertEquals(List.of("screen_replay_detected"), opened.reasons());
    }

    @Test
    void unusableImageIsInconclusive() {
        AuthenticityVerdict verdict = engine.applyAuthenticity(
                new AuthenticityFacts(false, null, List.of(new AlertScore("splicing", null))), open());
        assertEquals("REVIEW", verdict.issue());
        assertEquals(List.of("authenticity_inconclusive"), verdict.reasons());
        assertEquals(SignalOutcome.UNAVAILABLE, verdict.signals().get(0).outcome());
    }

    @Test
    void unavailableCallIsReviewWithoutAScore() {
        AuthenticityVerdict verdict = engine.authenticityUnavailable();
        assertEquals("REVIEW", verdict.issue());
        assertEquals(List.of("authenticity_unavailable"), verdict.reasons());
        assertEquals(
                new Signal("authenticity_unavailable", SignalOutcome.UNAVAILABLE, null), verdict.signals().get(0));
    }

    @Test
    void sandboxScenariosForceEachAlert() {
        assertEquals("splicing_detected", engine.decide("splicing").reasons().get(0));
        assertEquals("text_alteration_suspected", engine.decide("text_alteration").reasons().get(0));
        assertEquals("compression_inconsistency", engine.decide("compression").reasons().get(0));
        assertEquals("screen_replay_detected", engine.decide("screen_replay").reasons().get(0));
        assertEquals("printed_copy_detected", engine.decide("printed").reasons().get(0));
        assertEquals(VerificationDecision.DECLINED, engine.decide("printed").decision());
        assertEquals("tampering_suspected", engine.decide("authenticity_review").reasons().get(0));
        assertEquals(VerificationDecision.REVIEW, engine.decide("authenticity_review").decision());
        assertEquals(
                "authenticity_stub_pass", engine.decide("approved").signals().get(0).code());
    }

    @Test
    void mergeKeepsAVisionDecline() {
        var vision = new IdvDecisionEngine.Result(
                VerificationDecision.DECLINED,
                List.of("DOCUMENT_UNKNOWN"),
                List.of(new Signal("DOCUMENT_UNKNOWN", SignalOutcome.FAIL, null)),
                Map.of());
        var merged = engine.mergeAuthenticity(
                vision, engine.applyAuthenticity(measured(0.1, 0.0, 0.0, 0.0, 0.90, 0.0), open()));
        assertEquals(VerificationDecision.DECLINED, merged.decision());
        assertEquals("DOCUMENT_UNKNOWN", merged.reasons().get(0));
        assertEquals("screen_replay_detected", merged.signals().get(1).code());
    }

    @Test
    void mergeReviewDowngradesApproved() {
        var vision = new IdvDecisionEngine.Result(
                VerificationDecision.APPROVED, List.of(), List.of(), Map.of());
        var merged = engine.mergeAuthenticity(
                vision, engine.applyAuthenticity(measured(0.1, 0.0, 0.0, 0.0, 0.90, 0.0), open()));
        assertEquals(VerificationDecision.REVIEW, merged.decision());
        assertEquals("tampering_suspected", merged.reasons().get(0));
    }

    @Test
    void worseFaceWins() {
        AuthenticityVerdict pass = engine.applyAuthenticity(measured(1.0, 0.1, 0.1, 0.1, 0.1, 0.1), open());
        AuthenticityVerdict review = engine.applyAuthenticity(measured(0.1, 0.0, 0.0, 0.0, 0.90, 0.0), open());
        assertEquals("REVIEW", engine.worseAuthenticity(pass, review).issue());
    }

    private static AuthenticityPolicy open() {
        return AuthenticityPolicy.platform();
    }

    private static AuthenticityFacts measured(
            double authenticity, double splicing, double text, double compression, double screen, double printed) {
        return new AuthenticityFacts(
                true,
                authenticity,
                List.of(
                        new AlertScore("splicing", splicing),
                        new AlertScore("text_alteration", text),
                        new AlertScore("compression_inconsistency", compression),
                        new AlertScore("screen_replay", screen),
                        new AlertScore("printed_copy", printed)));
    }
}
