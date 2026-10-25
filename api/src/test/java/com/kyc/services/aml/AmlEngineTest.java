package com.kyc.services.aml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kyc.enums.ScreeningOutcome;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AmlEngineTest {

    private final DeterministicAmlEngine engine = new DeterministicAmlEngine();
    private final AmlPolicy policy = AmlPolicy.factory();

    @Test
    void referenceLines() {
        expect("Ivan", null, "Sanctionexample", "1971-04-12", null, null, ScreeningOutcome.CONFIRMED_MATCH, "1.000");
        expect("Ivan", null, "Sanctionexample", "1999-01-01", null, null, ScreeningOutcome.CLEAR, null);
        expect("Ivan", null, "Sanctionexample", null, null, null, ScreeningOutcome.POTENTIAL_MATCH, "1.000");
        expect("Ivan", null, "Sanctionexampl", "1971-04-12", null, null, ScreeningOutcome.CONFIRMED_MATCH, "1.000");
        expect("Ivan", null, "Sanctionexample", "1999-01-01", null, "X1234567", ScreeningOutcome.CONFIRMED_MATCH, "1.000");
        expect("Ivan", null, "Sanctionexample", "1971-04-12", null, "Z999", ScreeningOutcome.CLEAR, null);
        expect("Nora", null, "Potentialexample", null, null, null, ScreeningOutcome.POTENTIAL_MATCH, "1.000");
        expect("Nora", "P", "Example", null, null, null, ScreeningOutcome.POTENTIAL_MATCH, "1.000");
        expect("Nora", null, "Potentialexample", null, "US", null, ScreeningOutcome.POTENTIAL_MATCH, "0.900");
        expect("Leo", null, "Potentialexample", "2005-01-20", null, null, ScreeningOutcome.POTENTIAL_MATCH, "1.000");
        expect("Mina", null, "Debarexample", "1990-06-01", null, null, ScreeningOutcome.POTENTIAL_MATCH, "1.000");
        expect("Ada", null, "Clear", "1990-01-01", null, null, ScreeningOutcome.CLEAR, null);
        expect("Claire", null, "Martin", null, "FR", null, ScreeningOutcome.CLEAR, null);
        expect("Julien", null, "Mercier", "1971-04-12", "FR", null, ScreeningOutcome.CONFIRMED_MATCH, "1.000");
        expect("Camille", null, "Bernard", "1982-11-03", "FR", null, ScreeningOutcome.POTENTIAL_MATCH, "1.000");
        expect("Lucas", null, "Bernard", "2005-01-20", "FR", null, ScreeningOutcome.POTENTIAL_MATCH, "1.000");
        expect("Sophie", null, "Tremblay", "1990-01-01", "CA", null, ScreeningOutcome.POTENTIAL_MATCH, "1.000");
    }

    @Test
    void missingLastNameIsRejectedByTheSubjectParser() {
        var error = org.junit.jupiter.api.Assertions.assertThrows(
                SubjectParser.SubjectException.class,
                () -> SubjectParser.parse("Ivan", null, null, null, null, null, null, null));
        assertEquals("last_name", error.field());
        assertEquals("required", error.code());
    }

    @Test
    void engineDoesNotDependOnTheSanctionsPort() {
        for (var constructor : DeterministicAmlEngine.class.getConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                assertTrue(!parameter.getName().contains("SanctionsMatch"), parameter.getName());
            }
        }
    }

    private void expect(
            String first,
            String middle,
            String last,
            String dob,
            String nationality,
            String idNumber,
            ScreeningOutcome outcome,
            String score) {
        DeterministicAmlEngine.EngineResult result = engine.screen(
                new DeterministicAmlEngine.SubjectInput(
                        first,
                        middle,
                        last,
                        dob == null ? null : LocalDate.parse(dob),
                        nationality,
                        idNumber),
                policy);
        assertEquals(outcome, result.outcome(), first + " " + last);
        if (score == null) {
            assertTrue(result.hits().isEmpty(), first + " " + last);
            return;
        }
        assertEquals(new BigDecimal(score), result.hits().get(0).score(), first + " " + last);
    }
}
