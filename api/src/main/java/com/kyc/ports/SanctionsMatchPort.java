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
            List<String> datasets,
            Map<String, BigDecimal> providerFeatures) {}

    sealed interface MatchResult {
        String providerResponse();

        record Ok(List<Candidate> candidates, String providerResponse) implements MatchResult {
            public Ok(List<Candidate> candidates) {
                this(candidates, null);
            }
        }

        record Failed(String reason, String providerResponse) implements MatchResult {
            public Failed(String reason) {
                this(reason, null);
            }
        }
    }
}
