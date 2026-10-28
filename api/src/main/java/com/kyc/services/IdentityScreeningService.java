package com.kyc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.config.KycProperties;
import com.kyc.dto.idv.VerificationResponse;
import com.kyc.entities.Integration;
import com.kyc.entities.Verification;
import com.kyc.enums.AmlCategory;
import com.kyc.enums.IdentityScreeningEffect;
import com.kyc.enums.ScreeningOutcome;
import com.kyc.enums.ScreeningStatus;
import com.kyc.enums.VerificationDecision;
import com.kyc.enums.VerificationStatus;
import com.kyc.ports.SanctionsMatchPort;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.services.aml.Aml2Decider;
import com.kyc.services.aml.AmlPolicy;
import com.kyc.services.aml.DeterministicAmlEngine;
import com.kyc.services.aml.DeterministicAmlEngine.SubjectInput;
import com.kyc.services.aml.SubjectParser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IdentityScreeningService {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final IdvPolicyService policies;
    private final DeterministicAmlEngine engine;
    private final SanctionsMatchPort sanctions;
    private final Aml2Decider decider;
    private final IntegrationRepository integrations;
    private final KycProperties properties;
    private final ObjectMapper objectMapper;

    public IdentityScreeningService(
            IdvPolicyService policies,
            DeterministicAmlEngine engine,
            SanctionsMatchPort sanctions,
            Aml2Decider decider,
            IntegrationRepository integrations,
            KycProperties properties,
            ObjectMapper objectMapper) {
        this.policies = policies;
        this.engine = engine;
        this.sanctions = sanctions;
        this.decider = decider;
        this.integrations = integrations;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void apply(Verification verification, Integration identity, Instant now) {
        IdvPolicyService.Applied applied = policies.applied(verification.getPolicyId());
        if (!applied.screeningEnabled()) {
            return;
        }
        IdentityScreeningEffect effect = applied.screeningOnMatch();
        Ran ran = screen(verification, identity);
        verification.rememberScreening(writeReport(ran));
        if (ran.status != ScreeningStatus.COMPLETED || ran.outcome == null) {
            unavailable(verification, effect, now);
            return;
        }
        constrain(verification, effect, ran.outcome, now);
    }

    public void ensureReport(Verification verification) {
        String existing = verification.getScreeningReport();
        boolean missing = existing == null || existing.isBlank();
        boolean thin = !missing && !existing.contains("\"subject\"");
        if (!missing && !thin) {
            return;
        }
        if (verification.getDecision() == null || verification.getStatus() == VerificationStatus.PROCESSING) {
            return;
        }
        Integration identity = integrations.findById(verification.getIntegrationId()).orElse(null);
        if (identity == null) {
            return;
        }
        Ran ran = screen(verification, identity);
        if (thin && ran.status != ScreeningStatus.COMPLETED) {
            return;
        }
        verification.rememberScreening(writeReport(ran));
    }

    private Ran screen(Verification verification, Integration identity) {
        Map<String, Object> extracted = readMap(verification.getExtractedIdentity());
        String first = text(extracted, "first_name");
        String last = text(extracted, "last_name");
        if (first == null || last == null) {
            return Ran.failed("aml_unavailable");
        }
        SubjectInput subject;
        try {
            subject = SubjectParser.parse(
                            first,
                            text(extracted, "middle_name"),
                            last,
                            text(extracted, "birth_date"),
                            text(extracted, "document_country"),
                            null,
                            text(extracted, "document_number"),
                            idType(text(extracted, "document_type")))
                    .input();
        } catch (SubjectParser.SubjectException ex) {
            return Ran.failed("aml_unavailable");
        }
        AmlPolicy lists = AmlPolicy.factory();
        VerificationResponse.Subject screened = subjectOf(subject);
        try {
            if (!identity.isLive()) {
                DeterministicAmlEngine.EngineResult result = engine.screen(subject, lists);
                return Ran.completed(result.outcome(), screened, fixtureHits(result));
            }
            if (!properties.opensanctions().inService()) {
                return Ran.failed("provider_unavailable", screened);
            }
            SanctionsMatchPort.MatchResult match = sanctions.match(new SanctionsMatchPort.Query(
                    displayName(subject),
                    subject.dateOfBirth(),
                    subject.nationality(),
                    null,
                    subject.idNumber(),
                    lists.reviewThreshold()));
            Aml2Decider.Result decided = decider.decide(subject, lists, match);
            if (decided.status() != ScreeningStatus.COMPLETED || decided.outcome() == null) {
                return Ran.failed(
                        decided.failureReason() == null ? "provider_unavailable" : decided.failureReason(), screened);
            }
            return Ran.completed(decided.outcome(), screened, liveHits(decided.hits()));
        } catch (RuntimeException ex) {
            return Ran.failed("provider_unavailable", screened);
        }
    }

    private String writeReport(Ran ran) {
        String outcome = ran.outcome == null ? null : ran.outcome.name().toLowerCase(java.util.Locale.ROOT);
        VerificationResponse.Screening report = new VerificationResponse.Screening(
                ran.status == ScreeningStatus.COMPLETED ? "completed" : "failed",
                outcome,
                ran.failure,
                ran.subject,
                ran.hits);
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException ex) {
            return "{\"status\":\"failed\",\"failure_reason\":\"provider_unavailable\",\"hits\":[]}";
        }
    }

    private static List<VerificationResponse.Hit> fixtureHits(DeterministicAmlEngine.EngineResult result) {
        List<VerificationResponse.Hit> hits = new ArrayList<>();
        for (DeterministicAmlEngine.ScoredHit hit : result.hits()) {
            AmlCategory category = hit.categories().isEmpty() ? null : hit.categories().get(0);
            hits.add(new VerificationResponse.Hit(
                    hit.person().entityId(),
                    category == null ? "sanction" : category.json(),
                    categories(hit.categories()),
                    hit.score().doubleValue(),
                    hit.person().listedName(),
                    hit.matchedName(),
                    orEmpty(hit.person().aliases()),
                    one(hit.person().birth()),
                    one(hit.person().nationality()),
                    one(hit.person().dataset()),
                    orEmpty(hit.features())));
        }
        return hits;
    }

    private static List<VerificationResponse.Hit> liveHits(List<Aml2Decider.Hit> scored) {
        List<VerificationResponse.Hit> hits = new ArrayList<>();
        if (scored == null) {
            return hits;
        }
        for (Aml2Decider.Hit hit : scored) {
            hits.add(new VerificationResponse.Hit(
                    hit.entityId(),
                    hit.category() == null ? "sanction" : hit.category().json(),
                    categories(hit.categories()),
                    hit.score() == null ? 0 : hit.score().doubleValue(),
                    hit.listedName(),
                    hit.matchedName(),
                    orEmpty(hit.aliases()),
                    orEmpty(hit.birthDates()),
                    orEmpty(hit.countries()),
                    orEmpty(hit.datasets()),
                    orEmpty(hit.features())));
        }
        return hits;
    }

    private static final class Ran {
        private final ScreeningStatus status;
        private final ScreeningOutcome outcome;
        private final String failure;
        private final VerificationResponse.Subject subject;
        private final List<VerificationResponse.Hit> hits;

        private Ran(
                ScreeningStatus status,
                ScreeningOutcome outcome,
                String failure,
                VerificationResponse.Subject subject,
                List<VerificationResponse.Hit> hits) {
            this.status = status;
            this.outcome = outcome;
            this.failure = failure;
            this.subject = subject;
            this.hits = hits;
        }

        private static Ran completed(
                ScreeningOutcome outcome, VerificationResponse.Subject subject, List<VerificationResponse.Hit> hits) {
            return new Ran(ScreeningStatus.COMPLETED, outcome, null, subject, hits);
        }

        private static Ran failed(String reason) {
            return failed(reason, null);
        }

        private static Ran failed(String reason, VerificationResponse.Subject subject) {
            return new Ran(ScreeningStatus.FAILED, null, reason, subject, List.of());
        }
    }

    private void unavailable(Verification verification, IdentityScreeningEffect effect, Instant now) {
        if (effect == IdentityScreeningEffect.IGNORE) {
            return;
        }
        verification.applyScreening(
                VerificationDecision.REVIEW, write(append(verification.getDecisionReasons(), "aml_unavailable")), now);
    }

    private void constrain(
            Verification verification, IdentityScreeningEffect effect, ScreeningOutcome outcome, Instant now) {
        if (effect == IdentityScreeningEffect.IGNORE || outcome == ScreeningOutcome.CLEAR) {
            return;
        }
        String reason = outcome == ScreeningOutcome.CONFIRMED_MATCH ? "aml_confirmed_match" : "aml_potential_match";
        VerificationDecision decision = outcome == ScreeningOutcome.CONFIRMED_MATCH && effect == IdentityScreeningEffect.BLOCK
                ? VerificationDecision.DECLINED
                : VerificationDecision.REVIEW;
        verification.applyScreening(decision, write(append(verification.getDecisionReasons(), reason)), now);
    }

    private static VerificationResponse.Subject subjectOf(SubjectInput subject) {
        String birth = subject.dateOfBirth() == null ? null : subject.dateOfBirth().toString();
        return new VerificationResponse.Subject(
                subject.firstName(),
                subject.middleName(),
                subject.lastName(),
                birth,
                subject.nationality(),
                subject.idNumber());
    }

    private static List<String> categories(List<AmlCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return categories.stream().map(AmlCategory::json).toList();
    }

    private static List<String> one(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value);
    }

    private static List<String> orEmpty(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private static String displayName(SubjectInput subject) {
        return String.join(
                        " ",
                        Arrays.stream(new String[] {subject.firstName(), subject.middleName(), subject.lastName()})
                                .filter(part -> part != null && !part.isBlank())
                                .map(String::trim)
                                .toList())
                .replaceAll(" +", " ");
    }

    private List<String> append(String reasonsJson, String reason) {
        List<String> reasons = new ArrayList<>(readStrings(reasonsJson));
        reasons.add(reason);
        return reasons;
    }

    private String write(List<String> reasons) {
        try {
            return objectMapper.writeValueAsString(reasons);
        } catch (JsonProcessingException ex) {
            return "[\"" + reasons.get(reasons.size() - 1) + "\"]";
        }
    }

    private List<String> readStrings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRINGS);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String idType(String documentType) {
        if (documentType == null) {
            return null;
        }
        if (documentType.equalsIgnoreCase("passport")) {
            return "passport";
        }
        if (documentType.equalsIgnoreCase("national_id") || documentType.equalsIgnoreCase("id_card")) {
            return "national_id";
        }
        return "other";
    }
}
