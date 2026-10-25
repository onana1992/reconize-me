package com.kyc.services.aml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kyc.enums.AmlCategory;
import com.kyc.enums.ScreeningOutcome;
import com.kyc.enums.ScreeningStatus;
import com.kyc.ports.SanctionsMatchPort;
import com.kyc.services.aml.DeterministicAmlEngine.SubjectInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class Aml2DeciderTest {

    private final Aml2Decider decider = new Aml2Decider(new DeterministicAmlEngine());
    private final AmlPolicy policy = AmlPolicy.factory();

    @Test
    void emptyProviderListIsClear() {
        var result = decider.decide(ivan(), policy, new SanctionsMatchPort.MatchResult.Ok(List.of()));
        assertEquals(ScreeningOutcome.CLEAR, result.outcome());
        assertTrue(result.hits().isEmpty());
    }

    @Test
    void pepAtOneStaysPotential() {
        var result = decider.decide(nora(), policy, new SanctionsMatchPort.MatchResult.Ok(List.of(person(
                "os-pep",
                "1.0",
                List.of("role.pep"),
                List.of("Nora Potentialexample"),
                List.of("1982-11-03"),
                List.of("FR"),
                List.of()))));
        assertEquals(ScreeningOutcome.POTENTIAL_MATCH, result.outcome());
        assertEquals(AmlCategory.PEP, result.hits().get(0).category());
    }

    @Test
    void sanctionAndCrimeKeepsSanctionAsPrimary() {
        var result = decider.decide(ivan(), policy, new SanctionsMatchPort.MatchResult.Ok(List.of(person(
                "os-both",
                "1.000",
                List.of("crime", "sanction"),
                List.of("Ivan Sanctionexample"),
                List.of("1971-04-12"),
                List.of(),
                List.of("X1234567")))));
        assertEquals(ScreeningOutcome.CONFIRMED_MATCH, result.outcome());
        assertEquals(AmlCategory.SANCTION, result.hits().get(0).category());
        assertTrue(result.hits().get(0).categories().contains(AmlCategory.SANCTION));
        assertTrue(result.hits().get(0).categories().contains(AmlCategory.CRIME));
    }

    @Test
    void crimeOnlyIsDroppedWhenCrimeIsInactive() {
        var inactive = new AmlPolicy(
                policy.version(),
                policy.reviewThreshold(),
                policy.confirmThreshold(),
                java.util.EnumSet.of(AmlCategory.SANCTION, AmlCategory.PEP, AmlCategory.RCA, AmlCategory.DEBARMENT),
                policy.attachToIdentity(),
                policy.onMatch(),
                policy.testIntegrationId(),
                policy.liveIntegrationId());
        var result = decider.decide(ivan(), inactive, new SanctionsMatchPort.MatchResult.Ok(List.of(person(
                "os-crime",
                "0.990",
                List.of("crime.fraud"),
                List.of("Ivan Sanctionexample"),
                List.of("1971-04-12"),
                List.of(),
                List.of()))));
        assertEquals(ScreeningOutcome.CLEAR, result.outcome());
    }

    @Test
    void timeoutFailsClosed() {
        var result = decider.decide(ivan(), policy, new SanctionsMatchPort.MatchResult.Failed("provider_timeout"));
        assertEquals(ScreeningStatus.FAILED, result.status());
        assertNull(result.outcome());
        assertEquals("provider_timeout", result.failureReason());
    }

    @Test
    void missingIdOrScoreAboveThresholdFails() {
        var missingId = decider.decide(ivan(), policy, new SanctionsMatchPort.MatchResult.Ok(List.of(person(
                " ",
                "0.910",
                List.of("sanction"),
                List.of("Ivan Sanctionexample"),
                List.of("1971-04-12"),
                List.of(),
                List.of()))));
        assertEquals("provider_invalid_response", missingId.failureReason());

        var missingScore = decider.decide(ivan(), policy, new SanctionsMatchPort.MatchResult.Ok(List.of(new SanctionsMatchPort.Candidate(
                "os-1",
                null,
                "Person",
                true,
                List.of("sanction"),
                List.of("Ivan Sanctionexample"),
                List.of("1971-04-12"),
                List.of(),
                List.of(),
                List.of("sanctions")))));
        assertEquals(ScreeningStatus.FAILED, missingScore.status());
        assertNull(missingScore.outcome());
    }

    private static SubjectInput ivan() {
        return new SubjectInput("Ivan", null, "Sanctionexample", LocalDate.parse("1971-04-12"), null, "X1234567");
    }

    private static SubjectInput nora() {
        return new SubjectInput("Nora", null, "Potentialexample", LocalDate.parse("1982-11-03"), "FR", null);
    }

    private static SanctionsMatchPort.Candidate person(
            String id,
            String score,
            List<String> topics,
            List<String> names,
            List<String> births,
            List<String> nationalities,
            List<String> ids) {
        return new SanctionsMatchPort.Candidate(
                id,
                new BigDecimal(score),
                "Person",
                true,
                topics,
                names,
                births,
                nationalities,
                ids,
                List.of("opensanctions"));
    }
}
