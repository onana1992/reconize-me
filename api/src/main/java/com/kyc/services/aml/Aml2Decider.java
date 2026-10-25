package com.kyc.services.aml;

import com.kyc.enums.AmlCategory;
import com.kyc.enums.ScreeningOutcome;
import com.kyc.enums.ScreeningStatus;
import com.kyc.ports.SanctionsMatchPort;
import com.kyc.services.aml.DeterministicAmlEngine.SubjectInput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class Aml2Decider {

    private final DeterministicAmlEngine engine;

    public Aml2Decider(DeterministicAmlEngine engine) {
        this.engine = engine;
    }

    public Result decide(SubjectInput subject, AmlPolicy policy, SanctionsMatchPort.MatchResult match) {
        if (match instanceof SanctionsMatchPort.MatchResult.Failed failed) {
            return Result.failed(failed.reason());
        }
        SanctionsMatchPort.MatchResult.Ok ok = (SanctionsMatchPort.MatchResult.Ok) match;
        List<Hit> hits = new ArrayList<>();
        for (SanctionsMatchPort.Candidate candidate : ok.candidates()) {
            BigDecimal rounded = candidate.score() == null ? null : candidate.score().setScale(3, RoundingMode.HALF_UP);
            boolean above = rounded != null && rounded.compareTo(policy.reviewThreshold()) >= 0;
            if (!readable(candidate) || candidate.score() == null) {
                if (candidate.score() == null || above) {
                    return Result.failed("provider_invalid_response");
                }
                continue;
            }
            if (!above) {
                continue;
            }
            Set<AmlCategory> categories = categories(candidate.topics(), policy);
            if (categories.isEmpty()) {
                continue;
            }
            String listedName = candidate.names().isEmpty() ? "" : candidate.names().get(0);
            String birth = candidate.birthDates().isEmpty() ? null : candidate.birthDates().get(0);
            String nationality = candidate.nationalities().isEmpty() ? null : candidate.nationalities().get(0);
            String idNumber = candidate.idNumbers().isEmpty() ? null : candidate.idNumbers().get(0);
            List<String> features = engine.features(subject, listedName, birth, nationality, idNumber);
            boolean confirm = categories.contains(AmlCategory.SANCTION)
                    && rounded.compareTo(policy.confirmThreshold()) >= 0
                    && ((features.contains("name_exact") && features.contains("birth_date_exact"))
                            || features.contains("id_number_exact"));
            hits.add(new Hit(candidate.id(), primary(categories), List.copyOf(categories), rounded, features, confirm));
        }
        hits.sort(Comparator.comparing(Hit::score).reversed().thenComparing(Hit::entityId));
        if (hits.size() > 10) {
            hits = hits.subList(0, 10);
        }
        if (hits.isEmpty()) {
            return Result.clear();
        }
        boolean confirmed = hits.stream().anyMatch(Hit::autoConfirm);
        return new Result(
                ScreeningStatus.COMPLETED,
                confirmed ? ScreeningOutcome.CONFIRMED_MATCH : ScreeningOutcome.POTENTIAL_MATCH,
                null,
                List.copyOf(hits));
    }

    private static boolean readable(SanctionsMatchPort.Candidate candidate) {
        return candidate.id() != null
                && !candidate.id().isBlank()
                && candidate.target()
                && "Person".equals(candidate.schema());
    }

    private static Set<AmlCategory> categories(List<String> topics, AmlPolicy policy) {
        Set<AmlCategory> categories = EnumSet.noneOf(AmlCategory.class);
        if (topics == null) {
            return categories;
        }
        for (String topic : topics) {
            AmlCategory category = map(topic);
            if (category != null && policy.allows(category)) {
                categories.add(category);
            }
        }
        return categories;
    }

    private static AmlCategory map(String topic) {
        if (topic == null) {
            return null;
        }
        if (topic.equals("sanction") || topic.equals("sanction.linked") || topic.equals("asset.frozen")) {
            return AmlCategory.SANCTION;
        }
        if (topic.equals("role.pep")) {
            return AmlCategory.PEP;
        }
        if (topic.equals("role.rca")) {
            return AmlCategory.RCA;
        }
        if (topic.equals("debarment")) {
            return AmlCategory.DEBARMENT;
        }
        if (topic.equals("crime") || topic.startsWith("crime.")) {
            return AmlCategory.CRIME;
        }
        return null;
    }

    private static AmlCategory primary(Set<AmlCategory> categories) {
        for (AmlCategory category : AmlCategory.values()) {
            if (categories.contains(category)) {
                return category;
            }
        }
        return categories.iterator().next();
    }

    public record Hit(
            String entityId,
            AmlCategory category,
            List<AmlCategory> categories,
            BigDecimal score,
            List<String> features,
            boolean autoConfirm) {}

    public record Result(ScreeningStatus status, ScreeningOutcome outcome, String failureReason, List<Hit> hits) {
        static Result clear() {
            return new Result(ScreeningStatus.COMPLETED, ScreeningOutcome.CLEAR, null, List.of());
        }

        static Result failed(String reason) {
            return new Result(ScreeningStatus.FAILED, null, reason, List.of());
        }
    }
}
