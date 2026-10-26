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
    void aliasCanCarryTheExactName() {
        var result = decider.decide(ivan(), policy, new SanctionsMatchPort.MatchResult.Ok(List.of(person(
                "os-alias",
                "1.000",
                List.of("sanction"),
                List.of("Someone Else", "Ivan Sanctionexample"),
                List.of("1971-04-12"),
                List.of(),
                List.of()))));
        assertEquals(ScreeningOutcome.CONFIRMED_MATCH, result.outcome());
        assertTrue(result.hits().get(0).features().contains("name_exact"));
    }

    @Test
    void categoryFilterDropsNonTargetsAndTheHighestScoreDecides() {
        var noise = new SanctionsMatchPort.Candidate(
                "ru-inn-1",
                new BigDecimal("0.733"),
                "Person",
                false,
                List.of(),
                List.of("ВЛАДИМИР АЛЕКСАНДРОВИЧ ПУЛИН"),
                List.of(),
                List.of(),
                List.of(),
                List.of("ext_ru_egrul"),
                java.util.Map.of());
        var company = new SanctionsMatchPort.Candidate(
                "co-1",
                new BigDecimal("0.990"),
                "Company",
                true,
                List.of("sanction"),
                List.of("Ivan Sanctionexample"),
                List.of(),
                List.of(),
                List.of(),
                List.of("opensanctions"),
                java.util.Map.of());
        var pep = person(
                "os-pep",
                "0.990",
                List.of("role.pep"),
                List.of("Nora Potentialexample"),
                List.of("1982-11-03"),
                List.of("FR"),
                List.of());
        var sanction = person(
                "os-sanction",
                "0.960",
                List.of("sanction"),
                List.of("Ivan Sanctionexample"),
                List.of("1971-04-12"),
                List.of(),
                List.of("X1234567"));
        var result = decider.decide(ivan(), policy, new SanctionsMatchPort.MatchResult.Ok(List.of(noise, company, pep, sanction)));
        assertEquals(ScreeningOutcome.POTENTIAL_MATCH, result.outcome());
        assertEquals(2, result.hits().size());
        assertEquals("os-pep", result.hits().get(0).entityId());
        assertEquals(AmlCategory.PEP, result.hits().get(0).category());
    }

    @Test
    void enabledSanctionCategoryKeepsANonTargetPerson() {
        var listed = new SanctionsMatchPort.Candidate(
                "os-listed",
                new BigDecimal("1.000"),
                "Person",
                false,
                List.of("sanction"),
                List.of("Ivan Sanctionexample"),
                List.of("1971-04-12"),
                List.of(),
                List.of("X1234567"),
                List.of("opensanctions"),
                java.util.Map.of());
        var result = decider.decide(ivan(), policy, new SanctionsMatchPort.MatchResult.Ok(List.of(listed)));
        assertEquals(ScreeningOutcome.CONFIRMED_MATCH, result.outcome());
        assertEquals("os-listed", result.hits().get(0).entityId());
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
                List.of("sanctions"),
                java.util.Map.of()))));
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
                List.of("opensanctions"),
                java.util.Map.of());
    }
}
