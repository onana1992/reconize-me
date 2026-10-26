package com.kyc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.dto.aml.CreateScreeningRequest;
import com.kyc.dto.aml.DispositionRequest;
import com.kyc.dto.aml.ScreeningListResponse;
import com.kyc.dto.aml.ScreeningResponse;
import com.kyc.entities.AuditEvent;
import com.kyc.entities.Integration;
import com.kyc.entities.Screening;
import com.kyc.entities.ScreeningHit;
import com.kyc.entities.ScreeningIdempotencyKey;
import com.kyc.enums.AmlCategory;
import com.kyc.enums.HitDisposition;
import com.kyc.enums.ProductCode;
import com.kyc.enums.ScreeningOutcome;
import com.kyc.enums.ScreeningStatus;
import com.kyc.config.KycProperties;
import com.kyc.ports.SanctionsMatchPort;
import com.kyc.repositories.AuditEventRepository;
import com.kyc.repositories.ScreeningHitRepository;
import com.kyc.repositories.ScreeningIdempotencyRepository;
import com.kyc.repositories.ScreeningRepository;
import com.kyc.services.aml.Aml2;
import com.kyc.services.aml.Aml2Decider;
import com.kyc.services.aml.AmlNames;
import com.kyc.services.aml.AmlPolicy;
import com.kyc.services.aml.AmlPolicyService;
import com.kyc.services.aml.DeterministicAmlEngine;
import com.kyc.services.aml.SubjectParser;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScreeningService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningService.class);
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final TypeReference<Map<String, BigDecimal>> NUMBERS = new TypeReference<>() {};
    private static final Set<String> FALSE_REASONS =
            Set.of("different_person", "date_of_birth_conflict", "nationality_conflict", "name_only");
    private static final Set<String> TRUE_REASONS = Set.of("same_person", "listed_alias");

    private final ScreeningRepository screenings;
    private final ScreeningHitRepository hits;
    private final ScreeningIdempotencyRepository idempotency;
    private final AuditEventRepository auditEvents;
    private final DeterministicAmlEngine engine;
    private final Aml2Decider decider;
    private final SanctionsMatchPort sanctions;
    private final AmlPolicyService policies;
    private final CreditService credits;
    private final KycProperties properties;
    private final ObjectMapper objectMapper;

    public ScreeningService(
            ScreeningRepository screenings,
            ScreeningHitRepository hits,
            ScreeningIdempotencyRepository idempotency,
            AuditEventRepository auditEvents,
            DeterministicAmlEngine engine,
            Aml2Decider decider,
            SanctionsMatchPort sanctions,
            AmlPolicyService policies,
            CreditService credits,
            KycProperties properties,
            ObjectMapper objectMapper) {
        this.screenings = screenings;
        this.hits = hits;
        this.idempotency = idempotency;
        this.auditEvents = auditEvents;
        this.engine = engine;
        this.decider = decider;
        this.sanctions = sanctions;
        this.policies = policies;
        this.credits = credits;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResponseEntity<ScreeningResponse> create(
            Integration integration, String actorType, UUID actorId, CreateScreeningRequest request, String idempotencyKey) {
        requireAml(integration);
        requireLiveReady(integration);
        long started = System.nanoTime();
        String bodyHash = CryptoTokens.sha256Hex(write(request));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            validateIdempotencyKey(idempotencyKey);
            var existing = idempotency.findByOrganizationIdAndKey(integration.getOrganizationId(), idempotencyKey);
            if (existing.isPresent()) {
                if (!existing.get().getRequestHash().equals(bodyHash)) {
                    throw ApiException.conflict(
                            "idempotency_key_conflict", "Idempotency key reused with a different body");
                }
                Screening replay = require(integration, existing.get().getScreeningId());
                return ResponseEntity.ok(toResponse(replay, true));
            }
        }
        ParsedRequest parsed = parse(request);
        AmlPolicy policy = policies.effective(integration.getOrganizationId());
        Instant now = Instant.now();
        Screening screening = integration.isLive()
                ? persistLive(integration, parsed, policy, now)
                : persist(integration, parsed, policy, engine.screen(parsed.input(), policy), now);
        UUID id = screening.getId();
        if (integration.isLive() && screening.getStatus() == ScreeningStatus.COMPLETED) {
            credits.debitForLiveScreening(integration.getOrganizationId(), id, actorId, actorType);
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.save(new ScreeningIdempotencyKey(
                    integration.getOrganizationId(), idempotencyKey, bodyHash, id, now));
        }
        audit(integration.getOrganizationId(), actorType, actorId, "screening.created", id, "{}", now);
        if (screening.getStatus() == ScreeningStatus.FAILED) {
            audit(integration.getOrganizationId(), actorType, actorId, "screening.failed", id, "{}", now);
        }
        log.info(
                "screening.created id={} outcome={} failure={} duration_ms={}",
                id,
                screening.getOutcome(),
                screening.getFailureReason(),
                (System.nanoTime() - started) / 1_000_000);
        return new ResponseEntity<>(toResponse(screening, true), HttpStatus.CREATED);
    }

    @Transactional(readOnly = true)
    public ScreeningResponse get(Integration integration, UUID id) {
        requireAml(integration);
        return toResponse(require(integration, id), true);
    }

    @Transactional(readOnly = true)
    public ScreeningResponse getInOrg(UUID organizationId, UUID id) {
        Screening screening = screenings
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> ApiException.notFound("Screening not found"));
        return toResponse(screening, true);
    }

    @Transactional(readOnly = true)
    public ScreeningListResponse list(
            Integration integration, String status, String outcome, String externalId, String cursor, int limit) {
        requireAml(integration);
        return page(
                integration.getOrganizationId(),
                integration.getId(),
                status,
                outcome,
                externalId,
                cursor,
                limit);
    }

    @Transactional(readOnly = true)
    public ScreeningListResponse listOrg(
            UUID organizationId, UUID integrationId, String outcome, String cursor, int limit) {
        return page(organizationId, integrationId, null, outcome, null, cursor, limit);
    }

    @Transactional
    public ScreeningResponse dispose(
            Integration integration, String actorType, UUID actorId, UUID screeningId, UUID hitId, DispositionRequest request) {
        requireAml(integration);
        return disposeInOrg(integration.getOrganizationId(), actorType, actorId, screeningId, hitId, request, integration);
    }

    @Transactional
    public ScreeningResponse disposeInOrg(
            UUID organizationId,
            String actorType,
            UUID actorId,
            UUID screeningId,
            UUID hitId,
            DispositionRequest request,
            Integration scope) {
        Screening screening = scope == null
                ? screenings.findByIdAndOrganizationId(screeningId, organizationId)
                        .orElseThrow(() -> ApiException.notFound("Screening not found"))
                : require(scope, screeningId);
        if (screening.getStatus() != ScreeningStatus.COMPLETED) {
            throw ApiException.conflict("invalid_status", "A failed screening cannot be reviewed");
        }
        ScreeningHit hit = hits.findByIdAndScreeningId(hitId, screening.getId())
                .orElseThrow(() -> ApiException.notFound("Hit not found"));
        HitDisposition disposition = parseDisposition(request == null ? null : request.disposition());
        String reason = request == null ? null : request.reason();
        if (disposition == HitDisposition.FALSE_POSITIVE && (reason == null || !FALSE_REASONS.contains(reason))) {
            throw ApiException.validation("Invalid reason", List.of(new ErrorDetail("reason", "invalid")));
        }
        if (disposition == HitDisposition.TRUE_POSITIVE && (reason == null || !TRUE_REASONS.contains(reason))) {
            throw ApiException.validation("Invalid reason", List.of(new ErrorDetail("reason", "invalid")));
        }
        String note = request == null ? null : request.note();
        if (note != null && note.length() > 500) {
            throw ApiException.validation("Invalid note", List.of(new ErrorDetail("note", "size")));
        }
        HitDisposition previous = hit.getDisposition();
        hit.qualify(disposition, reason, note == null || note.isBlank() ? null : note);
        Instant now = Instant.now();
        ScreeningOutcome next = recompute(hits.findByScreeningIdOrderByScoreDescEntityIdAsc(screening.getId()));
        screening.replaceOutcome(next, now);
        audit(
                organizationId,
                actorType,
                actorId,
                "screening.disposition_set",
                screening.getId(),
                "{\"hit_id\":\"" + hitId + "\",\"from\":\"" + previous.name().toLowerCase(Locale.ROOT)
                        + "\",\"to\":\"" + disposition.name().toLowerCase(Locale.ROOT) + "\",\"reason\":\"" + reason + "\"}",
                now);
        return toResponse(screening, true);
    }

    @Transactional
    public Screening createForVerification(Integration amlIntegration, UUID verificationId, ParsedRequest parsed) {
        requireAml(amlIntegration);
        requireLiveReady(amlIntegration);
        AmlPolicy policy = policies.effective(amlIntegration.getOrganizationId());
        Instant now = Instant.now();
        ParsedRequest linked = new ParsedRequest(
                parsed.input(), parsed.country(), parsed.idType(), parsed.externalId(), verificationId);
        Screening screening = amlIntegration.isLive()
                ? persistLive(amlIntegration, linked, policy, now)
                : persist(amlIntegration, linked, policy, engine.screen(parsed.input(), policy), now);
        if (amlIntegration.isLive() && screening.getStatus() == ScreeningStatus.COMPLETED) {
            credits.debitForLiveScreening(amlIntegration.getOrganizationId(), screening.getId(), null, "system");
        }
        audit(amlIntegration.getOrganizationId(), "system", null, "screening.created", screening.getId(), "{}", now);
        if (screening.getStatus() == ScreeningStatus.FAILED) {
            audit(amlIntegration.getOrganizationId(), "system", null, "screening.failed", screening.getId(), "{}", now);
        }
        return screenings.findById(screening.getId()).orElseThrow();
    }

    private void requireLiveReady(Integration integration) {
        if (!integration.isLive()) {
            return;
        }
        if (!properties.opensanctions().inService()) {
            throw ApiException.conflict("engine_unavailable", "Live screening is not in service");
        }
        if (!credits.coversUnit(integration.getOrganizationId())) {
            throw ApiException.forbidden("insufficient_credit", "Live screening requires a credit balance");
        }
    }

    private Screening persistLive(Integration integration, ParsedRequest parsed, AmlPolicy policy, Instant now) {
        SanctionsMatchPort.MatchResult match = sanctions.match(new SanctionsMatchPort.Query(
                displayName(parsed),
                parsed.input().dateOfBirth(),
                parsed.input().nationality(),
                parsed.country(),
                parsed.input().idNumber(),
                policy.reviewThreshold()));
        Aml2Decider.Result decided = decider.decide(parsed.input(), policy, match);
        UUID id = UUID.randomUUID();
        Screening screening = new Screening(
                id,
                integration.getOrganizationId(),
                integration.getId(),
                parsed.externalId(),
                parsed.verificationId(),
                decided.outcome(),
                policy.version(),
                normalized(parsed),
                parsed.input().firstName(),
                parsed.input().lastName(),
                parsed.input().middleName(),
                parsed.input().dateOfBirth(),
                parsed.input().nationality(),
                parsed.country(),
                parsed.input().idNumber(),
                parsed.idType(),
                now);
        screening.useEngine(Aml2.ENGINE, Aml2.CORPUS, Aml2.ALGORITHM, match instanceof SanctionsMatchPort.MatchResult.Ok ? now : null);
        screening.rememberProviderResponse(match.providerResponse());
        if (decided.status() == ScreeningStatus.FAILED) {
            screening.fail(decided.failureReason(), now);
        }
        screenings.save(screening);
        if (decided.status() == ScreeningStatus.COMPLETED) {
            for (Aml2Decider.Hit scored : decided.hits()) {
                ScreeningHit hit = new ScreeningHit(
                        UUID.randomUUID(),
                        id,
                        scored.entityId(),
                        scored.category(),
                        join(scored.categories()),
                        scored.score(),
                        clip(scored.matchedName(), 256),
                        clip(scored.listedName(), 256),
                        write(scored.aliases()),
                        write(scored.birthDates()),
                        write(scored.countries()),
                        write(scored.datasets()),
                        write(scored.features()));
                hit.replaceProviderFeatures(write(scored.providerFeatures()));
                remember(hit, screening);
                hits.save(hit);
            }
        }
        return screening;
    }

    private static String displayName(ParsedRequest parsed) {
        return String.join(
                        " ",
                        java.util.Arrays.stream(new String[] {
                                    parsed.input().firstName(), parsed.input().middleName(), parsed.input().lastName()
                                })
                                .filter(part -> part != null && !part.isBlank())
                                .map(String::trim)
                                .toList())
                .replaceAll(" +", " ");
    }

    private Screening persist(
            Integration integration,
            ParsedRequest parsed,
            AmlPolicy policy,
            DeterministicAmlEngine.EngineResult result,
            Instant now) {
        UUID id = UUID.randomUUID();
        Screening screening = new Screening(
                id,
                integration.getOrganizationId(),
                integration.getId(),
                parsed.externalId(),
                parsed.verificationId(),
                result.outcome(),
                policy.version(),
                normalized(parsed),
                parsed.input().firstName(),
                parsed.input().lastName(),
                parsed.input().middleName(),
                parsed.input().dateOfBirth(),
                parsed.input().nationality(),
                parsed.country(),
                parsed.input().idNumber(),
                parsed.idType(),
                now);
        screenings.save(screening);
        for (var scored : result.hits()) {
            ScreeningHit hit = new ScreeningHit(
                    UUID.randomUUID(),
                    id,
                    scored.person().entityId(),
                    primary(scored.categories()),
                    join(scored.categories()),
                    scored.score(),
                    scored.matchedName(),
                    scored.person().listedName(),
                    write(scored.person().aliases()),
                    write(List.of(scored.person().birth())),
                    write(countries(scored.person().nationality())),
                    write(List.of(scored.person().dataset())),
                    write(scored.features()));
            remember(hit, screening);
            hits.save(hit);
        }
        return screening;
    }

    private ScreeningOutcome recompute(List<ScreeningHit> rows) {
        boolean truePositive = rows.stream().anyMatch(row -> row.getDisposition() == HitDisposition.TRUE_POSITIVE);
        if (truePositive) {
            return ScreeningOutcome.CONFIRMED_MATCH;
        }
        boolean open = rows.stream().anyMatch(row -> row.getDisposition() == HitDisposition.UNREVIEWED);
        if (!open) {
            return ScreeningOutcome.CLEAR;
        }
        return ScreeningOutcome.POTENTIAL_MATCH;
    }

    private void remember(ScreeningHit hit, Screening screening) {
        List<ScreeningHit> prior = hits.prior(
                screening.getOrganizationId(),
                screening.getNormalizedSubject(),
                hit.getEntityId(),
                screening.getId(),
                HitDisposition.UNREVIEWED);
        if (!prior.isEmpty()) {
            hit.remember(prior.get(0).getDisposition(), prior.get(0).getScreeningId());
        }
    }

    private ScreeningListResponse page(
            UUID organizationId,
            UUID integrationId,
            String status,
            String outcome,
            String externalId,
            String cursor,
            int limit) {
        int size = Math.min(Math.max(limit, 1), 100);
        Instant createdAt = Instant.parse("9999-12-31T00:00:00Z");
        UUID id = new UUID(Long.MAX_VALUE, Long.MAX_VALUE);
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split("\\|", 2);
            createdAt = Instant.parse(parts[0]);
            id = UUID.fromString(parts[1]);
        }
        List<Screening> rows = screenings.page(
                organizationId,
                integrationId,
                parseStatus(status),
                parseOutcome(outcome),
                externalId == null || externalId.isBlank() ? null : externalId,
                createdAt,
                id,
                PageRequest.of(0, size + 1));
        String next = null;
        if (rows.size() > size) {
            rows = new ArrayList<>(rows.subList(0, size));
            Screening last = rows.get(rows.size() - 1);
            next = last.getCreatedAt() + "|" + last.getId();
        }
        return new ScreeningListResponse(rows.stream().map(row -> toResponse(row, false)).toList(), next);
    }

    private ScreeningResponse toResponse(Screening screening, boolean detail) {
        List<ScreeningHit> stored = hits.findByScreeningIdOrderByScoreDescEntityIdAsc(screening.getId());
        List<ScreeningResponse.Hit> hitItems = detail ? stored.stream().map(this::hit).toList() : null;
        return new ScreeningResponse(
                screening.getId(),
                screening.getIntegrationId(),
                screening.getExternalId(),
                screening.getVerificationId(),
                screening.getStatus().name().toLowerCase(Locale.ROOT),
                screening.getOutcome() == null ? null : screening.getOutcome().name().toLowerCase(Locale.ROOT),
                screening.getFailureReason(),
                screening.getEngine(),
                screening.getPolicyVersion(),
                screening.getCorpusVersion(),
                screening.getRetrievedAt(),
                new ScreeningResponse.Subject(
                        screening.getSubjectType(),
                        screening.getFirstName(),
                        screening.getLastName(),
                        screening.getMiddleName(),
                        screening.getDateOfBirth() == null ? null : screening.getDateOfBirth().toString(),
                        screening.getNationality(),
                        screening.getCountryOfResidence(),
                        screening.getIdNumber(),
                        screening.getIdType()),
                hitItems,
                stored.size(),
                screening.getCreatedAt(),
                screening.getUpdatedAt(),
                detail ? screening.getProviderResponse() : null);
    }

    private ScreeningResponse.Hit hit(ScreeningHit row) {
        return new ScreeningResponse.Hit(
                row.getId(),
                row.getEntityId(),
                row.getCategory().json(),
                split(row.getCategories()),
                row.getScore(),
                row.getMatchedName(),
                row.getListedName(),
                readStrings(row.getAliasesJson()),
                readStrings(row.getBirthDatesJson()),
                readStrings(row.getCountriesJson()),
                readStrings(row.getDatasetsJson()),
                readStrings(row.getFeaturesJson()),
                readNumbers(row.getProviderFeaturesJson()),
                row.getDisposition().name().toLowerCase(Locale.ROOT),
                row.getDispositionReason(),
                row.getPriorDisposition() == null ? null : row.getPriorDisposition().name().toLowerCase(Locale.ROOT),
                row.getPriorScreeningId());
    }

    private ParsedRequest parse(CreateScreeningRequest request) {
        if (request != null && request.type() != null && !request.type().isBlank() && !"person".equals(request.type())) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "unsupported_subject", "Only a person can be screened");
        }
        String externalId = request == null ? null : blank(request.externalId());
        if (externalId != null && externalId.length() > 128) {
            throw ApiException.validation("Invalid external_id", List.of(new ErrorDetail("external_id", "size")));
        }
        try {
            SubjectParser.Parsed parsed = SubjectParser.parse(
                    request == null ? null : request.firstName(),
                    request == null ? null : request.middleName(),
                    request == null ? null : request.lastName(),
                    request == null ? null : request.dateOfBirth(),
                    request == null ? null : request.nationality(),
                    request == null ? null : request.countryOfResidence(),
                    request == null ? null : request.idNumber(),
                    request == null ? null : request.idType());
            return new ParsedRequest(parsed.input(), parsed.countryOfResidence(), parsed.idType(), externalId, null);
        } catch (SubjectParser.SubjectException ex) {
            throw ApiException.validation("Invalid subject", List.of(new ErrorDetail(ex.field(), ex.code())));
        }
    }

    private static String normalized(ParsedRequest parsed) {
        return AmlNames.queryName(parsed.input().firstName(), parsed.input().middleName(), parsed.input().lastName())
                + "|"
                + (parsed.input().dateOfBirth() == null ? "" : parsed.input().dateOfBirth())
                + "|"
                + (parsed.input().nationality() == null ? "" : parsed.input().nationality())
                + "|"
                + AmlNames.normalizeId(parsed.input().idNumber());
    }

    private Screening require(Integration integration, UUID id) {
        return screenings
                .findByIdAndOrganizationIdAndIntegrationId(id, integration.getOrganizationId(), integration.getId())
                .orElseThrow(() -> ApiException.notFound("Screening not found"));
    }

    private static void requireAml(Integration integration) {
        if (integration.getProduct() != ProductCode.AML) {
            throw ApiException.unauthorized("unauthorized", "API key is not valid");
        }
    }

    private void audit(UUID organizationId, String actorType, UUID actorId, String action, UUID id, String payload, Instant now) {
        auditEvents.save(new AuditEvent(organizationId, actorId == null ? "system" : actorType, actorId, action, "screening", id, payload, now));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private Map<String, BigDecimal> readNumbers(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, NUMBERS);
        } catch (JsonProcessingException ex) {
            return Map.of();
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

    private static List<String> split(String categories) {
        if (categories == null || categories.isBlank()) {
            return List.of();
        }
        return List.of(categories.split(","));
    }

    private static String join(List<AmlCategory> categories) {
        return String.join(",", categories.stream().map(AmlCategory::json).toList());
    }

    private static AmlCategory primary(List<AmlCategory> categories) {
        for (AmlCategory category : AmlCategory.values()) {
            if (categories.contains(category)) {
                return category;
            }
        }
        return categories.get(0);
    }

    private static List<String> countries(String nationality) {
        return nationality == null ? List.of() : List.of(nationality);
    }

    private static HitDisposition parseDisposition(String raw) {
        if ("false_positive".equals(raw)) {
            return HitDisposition.FALSE_POSITIVE;
        }
        if ("true_positive".equals(raw)) {
            return HitDisposition.TRUE_POSITIVE;
        }
        throw ApiException.validation("Invalid disposition", List.of(new ErrorDetail("disposition", "invalid")));
    }

    private static ScreeningStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ScreeningStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation("Invalid status", List.of(new ErrorDetail("status", "invalid")));
        }
    }

    private static ScreeningOutcome parseOutcome(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ScreeningOutcome.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation("Invalid outcome", List.of(new ErrorDetail("outcome", "invalid")));
        }
    }

    private static void validateIdempotencyKey(String key) {
        if (key.length() < 8 || key.length() > 64 || !key.chars().allMatch(ch -> ch >= 32 && ch <= 126)) {
            throw ApiException.validation("Invalid Idempotency-Key", List.of(new ErrorDetail("Idempotency-Key", "invalid")));
        }
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ParsedRequest(
            DeterministicAmlEngine.SubjectInput input,
            String country,
            String idType,
            String externalId,
            UUID verificationId) {}
}
