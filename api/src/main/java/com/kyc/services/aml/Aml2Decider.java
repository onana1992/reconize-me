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
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
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
            if (candidate.score() == null) {
                return Result.failed("provider_invalid_response");
            }
            if (!"Person".equals(candidate.schema())) {
                continue;
            }
            if (candidate.id() == null || candidate.id().isBlank()) {
                if (above) {
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
            List<String> names = candidate.names() == null ? List.of() : candidate.names();
            String listedName = names.isEmpty() ? "" : names.get(0);
            String matchedSource = bestName(subject, names, listedName);
            String birth = bestBirth(subject, matchedSource, candidate.birthDates());
            String nationality = bestNationality(subject, matchedSource, birth, candidate.nationalities());
            String idNumber = bestId(subject, matchedSource, birth, nationality, candidate.idNumbers());
            List<String> features = engine.features(subject, matchedSource, birth, nationality, idNumber);
            String matchedName = engine.scoreName(subject, matchedSource).matchedName();
            boolean confirm = categories.contains(AmlCategory.SANCTION)
                    && rounded.compareTo(policy.confirmThreshold()) >= 0
                    && ((features.contains("name_exact") && features.contains("birth_date_exact"))
                            || features.contains("id_number_exact"));
            List<String> aliases = names.size() <= 1 ? List.of() : List.copyOf(names.subList(1, Math.min(names.size(), 21)));
            hits.add(new Hit(
                    candidate.id(),
                    primary(categories),
                    List.copyOf(categories),
                    rounded,
                    matchedName,
                    listedName,
                    aliases,
                    copy(candidate.birthDates()),
                    copy(candidate.nationalities()),
                    copy(candidate.datasets()),
                    features,
                    candidate.providerFeatures() == null ? Map.of() : Map.copyOf(candidate.providerFeatures()),
                    confirm));
        }
        hits.sort(Comparator.comparing(Hit::score).reversed().thenComparing(Hit::entityId));
        if (hits.size() > 10) {
            hits = hits.subList(0, 10);
        }
        if (hits.isEmpty()) {
            return Result.clear();
        }
        boolean confirmed = hits.get(0).autoConfirm();
        return new Result(
                ScreeningStatus.COMPLETED,
                confirmed ? ScreeningOutcome.CONFIRMED_MATCH : ScreeningOutcome.POTENTIAL_MATCH,
                null,
                List.copyOf(hits));
    }

    private String bestName(SubjectInput subject, List<String> names, String fallback) {
        String best = fallback;
        BigDecimal score = BigDecimal.ZERO;
        for (String name : names) {
            BigDecimal next = engine.scoreName(subject, name).score();
            if (next.compareTo(score) > 0) {
                score = next;
                best = name;
            }
        }
        return best;
    }

    private String bestBirth(SubjectInput subject, String name, List<String> births) {
        return best(births, birth -> rankBirth(engine.features(subject, name, birth, null, null)));
    }

    private String bestNationality(SubjectInput subject, String name, String birth, List<String> nationalities) {
        return best(nationalities, nationality -> rankNationality(engine.features(subject, name, birth, nationality, null)));
    }

    private String bestId(SubjectInput subject, String name, String birth, String nationality, List<String> ids) {
        return best(ids, id -> rankId(engine.features(subject, name, birth, nationality, id)));
    }

    private static String best(List<String> values, java.util.function.ToIntFunction<String> rank) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String chosen = values.get(0);
        int best = rank.applyAsInt(chosen);
        for (int i = 1; i < values.size(); i++) {
            int next = rank.applyAsInt(values.get(i));
            if (next > best) {
                best = next;
                chosen = values.get(i);
            }
        }
        return chosen;
    }

    private static int rankBirth(List<String> features) {
        if (features.contains("birth_date_exact")) {
            return 4;
        }
        if (features.contains("birth_year_match")) {
            return 3;
        }
        if (features.contains("birth_date_partial")) {
            return 2;
        }
        if (features.contains("birth_date_absent")) {
            return 1;
        }
        return 0;
    }

    private static int rankNationality(List<String> features) {
        if (features.contains("nationality_match")) {
            return 2;
        }
        return features.contains("nationality_mismatch") ? 0 : 1;
    }

    private static int rankId(List<String> features) {
        if (features.contains("id_number_exact")) {
            return 2;
        }
        return features.contains("id_number_mismatch") ? 0 : 1;
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
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
            String matchedName,
            String listedName,
            List<String> aliases,
            List<String> birthDates,
            List<String> countries,
            List<String> datasets,
            List<String> features,
            Map<String, BigDecimal> providerFeatures,
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
