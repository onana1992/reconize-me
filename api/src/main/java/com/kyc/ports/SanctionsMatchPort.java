package com.kyc.ports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface SanctionsMatchPort {

    MatchResult match(Query query);

    record Query(
            String name,
            LocalDate birth,
            String nationality,
            String countryOfResidence,
            String idNumber,
            BigDecimal threshold) {}

    record Candidate(
            String id,
            BigDecimal score,
            String schema,
            boolean target,
            List<String> topics,
            List<String> names,
            List<String> birthDates,
            List<String> nationalities,
            List<String> idNumbers,
            List<String> datasets) {}

    sealed interface MatchResult {
        record Ok(List<Candidate> candidates) implements MatchResult {}

        record Failed(String reason) implements MatchResult {}
    }
}
