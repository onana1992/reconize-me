package com.kyc.services.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.kyc.enums.AuthenticationStatus;
import com.kyc.services.auth.AuthDecisionEngine.Candidate;
import com.kyc.services.auth.AuthDecisionEngine.Result;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthDecisionEngineTest {

    private final AuthDecisionEngine engine = new AuthDecisionEngine();
    private final AuthPolicy policy = AuthPolicy.factory();

    @Test
    void otherFaceIdAtHighScoreIsFaceMatchFail() {
        Result result = engine.decide(
                true,
                "face-session",
                false,
                true,
                List.of(new Candidate("face-other", 96f)),
                null,
                policy);
        assertEquals(AuthenticationStatus.DECLINED, result.status());
        assertEquals(List.of("face_match_fail"), result.reasons());
        assertNull(result.matchScore());
    }

    @Test
    void sameFaceIdAtApprovalFloorIsApproved() {
        Result result = engine.decide(
                true,
                "face-session",
                false,
                true,
                List.of(new Candidate("face-session", 90f)),
                null,
                policy);
        assertEquals(AuthenticationStatus.APPROVED, result.status());
        assertEquals(List.of("liveness_pass", "face_match_pass"), result.reasons());
        assertEquals(new BigDecimal("0.900"), result.matchScore());
    }

    @Test
    void presenceBelowGateReceivesNoCandidates() {
        // Upstream must not call Search; engine sees empty candidates only after a frank attack
        // path is separate. Below-gate without candidates is not an approved shortcut.
        Result result = engine.decide(true, "face-session", false, false, List.of(), null, policy);
        assertEquals(AuthenticationStatus.DECLINED, result.status());
        assertEquals(List.of("presentation_attack"), result.reasons());
        assertNull(result.matchScore());
    }

    @Test
    void enrollmentInactiveBeforeSearch() {
        Result result = engine.decide(
                false,
                "face-session",
                false,
                true,
                List.of(new Candidate("face-session", 99f)),
                null,
                policy);
        assertEquals(AuthenticationStatus.DECLINED, result.status());
        assertEquals(List.of("enrollment_inactive"), result.reasons());
        assertNull(result.matchScore());
    }

    @Test
    void presentationAttackHasNoScore() {
        Result result = engine.decide(
                true,
                "face-session",
                true,
                false,
                List.of(new Candidate("face-session", 99f)),
                null,
                policy);
        assertEquals(AuthenticationStatus.DECLINED, result.status());
        assertEquals(List.of("presentation_attack"), result.reasons());
        assertNull(result.matchScore());
    }

    @Test
    void scoreBelowReviewIsFaceMatchFail() {
        Result result = engine.decide(
                true,
                "face-session",
                false,
                true,
                List.of(new Candidate("face-session", 79.4f)),
                null,
                policy);
        assertEquals(AuthenticationStatus.DECLINED, result.status());
        assertEquals(List.of("face_match_fail"), result.reasons());
        assertEquals(new BigDecimal("0.794"), result.matchScore());
    }

    @Test
    void grayBandIsDeclined() {
        Result declined = engine.decide(
                true,
                "face-session",
                false,
                true,
                List.of(new Candidate("face-session", 85f)),
                null,
                policy);
        assertEquals(AuthenticationStatus.DECLINED, declined.status());
        assertEquals(List.of("face_match_fail"), declined.reasons());
    }

    @Test
    void providerTimeoutIsFailedNeverApproved() {
        Result result = engine.decide(
                true,
                "face-session",
                false,
                true,
                List.of(new Candidate("face-session", 99f)),
                "provider_timeout",
                policy);
        assertEquals(AuthenticationStatus.FAILED, result.status());
        assertEquals("provider_timeout", result.failureReason());
        assertEquals(List.of(), result.reasons());
        assertNull(result.matchScore());
    }

    @Test
    void scoreRoundsHalfUpToThreeDecimals() {
        assertEquals(new BigDecimal("0.896"), AuthDecisionEngine.toScore(89.55f));
        assertEquals(new BigDecimal("0.900"), AuthDecisionEngine.toScore(90f));
    }
}
