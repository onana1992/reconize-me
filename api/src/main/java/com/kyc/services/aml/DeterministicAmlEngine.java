package com.kyc.services.aml;

import com.kyc.enums.AmlCategory;
import com.kyc.enums.ScreeningOutcome;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DeterministicAmlEngine {

    private static final BigDecimal DOB_YEAR_MISMATCH = new BigDecimal("0.15");
    private static final BigDecimal DOB_PARTIAL = new BigDecimal("0.85");
    private static final BigDecimal NATIONALITY_MISMATCH = new BigDecimal("0.90");
    private static final BigDecimal ID_MISMATCH = new BigDecimal("0.20");
    private static final BigDecimal ID_NAME_FLOOR = new BigDecimal("0.50");
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP);

    private final List<ListedPerson> corpus;

    public DeterministicAmlEngine() {
        this(FixtureCorpus.fixture1());
    }

    DeterministicAmlEngine(List<ListedPerson> corpus) {
        this.corpus = List.copyOf(corpus);
    }

    public EngineResult screen(SubjectInput subject, AmlPolicy policy) {
        List<ScoredHit> scored = new ArrayList<>();
        for (ListedPerson person : corpus) {
            ScoredHit hit = score(subject, person, policy);
            if (hit != null && hit.score().compareTo(policy.reviewThreshold()) >= 0) {
                scored.add(hit);
            }
        }
        scored.sort(Comparator.comparing(ScoredHit::score).reversed().thenComparing(hit -> hit.person().entityId()));
        if (scored.size() > 10) {
            scored = scored.subList(0, 10);
        }
        boolean confirmed = scored.stream().anyMatch(ScoredHit::autoConfirm);
        ScreeningOutcome outcome = scored.isEmpty()
                ? ScreeningOutcome.CLEAR
                : confirmed ? ScreeningOutcome.CONFIRMED_MATCH : ScreeningOutcome.POTENTIAL_MATCH;
        return new EngineResult(outcome, List.copyOf(scored));
    }

    public List<String> features(
            SubjectInput subject, String listedName, String birth, String nationality, String idNumber) {
        Variant best = scoreVariant(subject.queryName(), AmlNames.normalize(listedName));
        return featureList(subject, best, birth, nationality, idNumber);
    }

    private ScoredHit score(SubjectInput subject, ListedPerson person, AmlPolicy policy) {
        List<AmlCategory> categories = person.categories().stream().filter(policy::allows).toList();
        if (categories.isEmpty()) {
            return null;
        }
        Variant best = null;
        for (String variant : person.variants()) {
            Variant scored = scoreVariant(subject.queryName(), variant);
            if (best == null || scored.nameScore().compareTo(best.nameScore()) > 0) {
                best = scored;
            }
        }
        if (best == null) {
            return null;
        }
        List<String> features = featureList(subject, best, person.birth(), person.nationality(), person.idNumber());
        DobFactor dobFactor = dob(subject.dateOfBirth(), person.birth());
        NatFactor nationalityFactor = nationality(subject.nationality(), person.nationality());
        BigDecimal score;
        if (features.contains("id_number_exact")) {
            score = BigDecimal.ONE.setScale(3, RoundingMode.HALF_UP);
        } else {
            BigDecimal idFactor = features.contains("id_number_mismatch") ? ID_MISMATCH : BigDecimal.ONE;
            score = best.nameScore()
                    .multiply(dobFactor.factor())
                    .multiply(nationalityFactor.factor())
                    .multiply(idFactor)
                    .setScale(3, RoundingMode.HALF_UP);
        }
        boolean autoConfirm = categories.contains(AmlCategory.SANCTION)
                && score.compareTo(policy.confirmThreshold()) >= 0
                && ((features.contains("name_exact") && features.contains("birth_date_exact"))
                        || features.contains("id_number_exact"));
        return new ScoredHit(person, categories, score, best.matchedName(), features, autoConfirm);
    }

    private static List<String> featureList(
            SubjectInput subject, Variant best, String birth, String listedNationality, String listedIdNumber) {
        List<String> features = new ArrayList<>();
        BigDecimal nameScore = best.nameScore();
        if (nameScore.compareTo(ONE) == 0) {
            features.add("name_exact");
        } else if (nameScore.signum() > 0) {
            features.add("name_partial");
        }
        features.add(dob(subject.dateOfBirth(), birth).feature());
        NatFactor nationality = nationality(subject.nationality(), listedNationality);
        if (nationality.feature() != null) {
            features.add(nationality.feature());
        }
        String queryId = AmlNames.normalizeId(subject.idNumber());
        String listedId = AmlNames.normalizeId(listedIdNumber);
        if (!queryId.isEmpty() && queryId.equals(listedId) && nameScore.compareTo(ID_NAME_FLOOR) >= 0) {
            features.add("id_number_exact");
        } else if (!queryId.isEmpty() && !listedId.isEmpty() && !queryId.equals(listedId)) {
            features.add("id_number_mismatch");
        }
        return features;
    }

    private static Variant scoreVariant(String query, String variant) {
        List<String> left = AmlNames.tokens(query);
        List<String> right = AmlNames.tokens(variant);
        if (left.isEmpty() || right.isEmpty()) {
            return new Variant(variant, BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP));
        }
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < left.size(); i++) {
            for (int j = 0; j < right.size(); j++) {
                if (matches(left.get(i), right.get(j))) {
                    pairs.add(new Pair(i, j, left.get(i).equals(right.get(j)), right.get(j).length()));
                }
            }
        }
        pairs.sort(Comparator.comparing(Pair::exact)
                .reversed()
                .thenComparing(Comparator.comparingInt(Pair::length).reversed())
                .thenComparingInt(Pair::query)
                .thenComparingInt(Pair::candidate));
        boolean[] usedLeft = new boolean[left.size()];
        boolean[] usedRight = new boolean[right.size()];
        int taken = 0;
        for (Pair pair : pairs) {
            if (usedLeft[pair.query()] || usedRight[pair.candidate()]) {
                continue;
            }
            usedLeft[pair.query()] = true;
            usedRight[pair.candidate()] = true;
            taken++;
        }
        BigDecimal nameScore = BigDecimal.valueOf(2L * taken)
                .divide(BigDecimal.valueOf(left.size() + right.size()), 6, RoundingMode.HALF_UP);
        return new Variant(variant, nameScore);
    }

    private static boolean matches(String queryToken, String candidateToken) {
        if (queryToken.equals(candidateToken)) {
            return true;
        }
        return queryToken.length() >= 5
                && candidateToken.length() >= 5
                && AmlNames.levenshtein(queryToken, candidateToken) == 1;
    }

    private static DobFactor dob(LocalDate query, String listed) {
        if (query == null || listed == null || listed.isBlank()) {
            return new DobFactor(BigDecimal.ONE, "birth_date_absent");
        }
        boolean listedFull = listed.length() == 10;
        int listedYear = Integer.parseInt(listed.substring(0, 4));
        if (query.getYear() != listedYear) {
            return new DobFactor(DOB_YEAR_MISMATCH, "birth_year_mismatch");
        }
        if (!listedFull) {
            return new DobFactor(BigDecimal.ONE, "birth_year_match");
        }
        if (query.toString().equals(listed)) {
            return new DobFactor(BigDecimal.ONE, "birth_date_exact");
        }
        return new DobFactor(DOB_PARTIAL, "birth_date_partial");
    }

    private static NatFactor nationality(String query, String listed) {
        String left = query == null ? "" : query.trim().toUpperCase(java.util.Locale.ROOT);
        String right = listed == null ? "" : listed.trim().toUpperCase(java.util.Locale.ROOT);
        if (left.isEmpty() || right.isEmpty()) {
            return new NatFactor(BigDecimal.ONE, null);
        }
        if (left.equals(right)) {
            return new NatFactor(BigDecimal.ONE, "nationality_match");
        }
        return new NatFactor(NATIONALITY_MISMATCH, "nationality_mismatch");
    }

    public record SubjectInput(
            String firstName,
            String middleName,
            String lastName,
            LocalDate dateOfBirth,
            String nationality,
            String idNumber) {

        public String queryName() {
            return AmlNames.queryName(firstName, middleName, lastName);
        }
    }

    public record EngineResult(ScreeningOutcome outcome, List<ScoredHit> hits) {}

    public record ScoredHit(
            ListedPerson person,
            List<AmlCategory> categories,
            BigDecimal score,
            String matchedName,
            List<String> features,
            boolean autoConfirm) {}

    private record Variant(String matchedName, BigDecimal nameScore) {}

    private record Pair(int query, int candidate, boolean exact, int length) {}

    private record DobFactor(BigDecimal factor, String feature) {}

    private record NatFactor(BigDecimal factor, String feature) {}
}
