package com.kyc.authenticity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuthenticityVerdictTest {

    @Test
    void alignmentFailureAsksForReviewAndNeverSuspect() {
        AuthenticityVerdict.Decision decision = AuthenticityVerdict.alignmentFailed();

        assertEquals("a_verifier", decision.verdict());
        assertEquals(List.of("alignment_failed"), decision.codes());
    }

    @Test
    void everyConformeMeasurementPasses() {
        AuthenticityVerdict.Decision decision = AuthenticityVerdict.decide(List.of(
                new AuthenticityVerdict.Reading("conforme", null),
                new AuthenticityVerdict.Reading("conforme", null)));

        assertEquals("conforme", decision.verdict());
        assertEquals(List.of(), decision.codes());
    }

    @Test
    void aMinorityOfSuspectZonesStaysConforme() {
        AuthenticityVerdict.Decision decision = AuthenticityVerdict.decide(List.of(
                new AuthenticityVerdict.Reading("conforme", null),
                new AuthenticityVerdict.Reading("conforme", null),
                new AuthenticityVerdict.Reading("suspect", null),
                new AuthenticityVerdict.Reading("a_verifier", null)));

        assertEquals("conforme", decision.verdict());
        assertEquals(List.of(), decision.codes());
    }

    @Test
    void halfTheZonesSuspectFailsTheDocument() {
        AuthenticityVerdict.Decision decision = AuthenticityVerdict.decide(List.of(
                new AuthenticityVerdict.Reading("conforme", null),
                new AuthenticityVerdict.Reading("suspect", null),
                new AuthenticityVerdict.Reading("suspect", "field_absent")));

        assertEquals("suspect", decision.verdict());
        assertEquals(List.of("field_absent"), decision.codes());
    }

    @Test
    void aMissingFieldKeepsItsCode() {
        AuthenticityVerdict.Decision decision = AuthenticityVerdict.decide(List.of(
                new AuthenticityVerdict.Reading("suspect", "field_absent"),
                new AuthenticityVerdict.Reading("a_verifier", "zone_unmeasured")));

        assertEquals("suspect", decision.verdict());
        assertEquals(List.of("field_absent"), decision.codes());
    }

    @Test
    void aBorderlineMeasureAsksForReviewWhenNothingIsSuspect() {
        AuthenticityVerdict.Decision decision = AuthenticityVerdict.decide(List.of(
                new AuthenticityVerdict.Reading("conforme", null),
                new AuthenticityVerdict.Reading("a_verifier", "zone_unmeasured"),
                new AuthenticityVerdict.Reading("a_verifier", null)));

        assertEquals("a_verifier", decision.verdict());
        assertEquals(List.of("zone_unmeasured"), decision.codes());
    }
}
