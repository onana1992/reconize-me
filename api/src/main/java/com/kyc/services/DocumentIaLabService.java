package com.kyc.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.config.KycProperties;
import com.kyc.dto.documentia.DocumentDecision;
import com.kyc.dto.documentia.DocumentIaAnalysisResponse;
import com.kyc.dto.documentia.DocumentIaCatalogResponse;
import com.kyc.dto.documentia.DocumentImageQuality;
import com.kyc.dto.documentia.DocumentParse;
import com.kyc.dto.documentia.DocumentScores;
import com.kyc.dto.documentia.MrzReport;
import com.kyc.ports.VisionDocumentPort;
import com.kyc.security.ApiPrincipal;
import com.kyc.services.documentia.ConfidenceScorer;
import com.kyc.services.documentia.DocumentAnalysisParser;
import com.kyc.services.documentia.DocumentValidator;
import com.kyc.services.documentia.FieldNormalizer;
import com.kyc.services.documentia.ImageQualityGate;
import com.kyc.services.documentia.InvalidModelJsonException;
import com.kyc.services.documentia.MrzEngine;
import com.kyc.services.documentia.SchemaEdition;
import com.kyc.services.documentia.SchemaRegistry;
import com.kyc.services.documentia.VisionAttempts;
import com.kyc.services.documentia.VisionPrompt;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentIaLabService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIaLabService.class);

    static final Set<String> STAGES = Set.of("quality", "parse", "vision", "normalize", "mrz", "validate", "decide");

    private static final Pattern FIXTURE_NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final KycProperties properties;
    private final IntegrationService integrations;
    private final DocumentAnalysisParser parser;
    private final SchemaRegistry registry;
    private final ImageQualityGate qualityGate;
    private final VisionDocumentPort vision;
    private final ObjectMapper objectMapper;
    private final IdvDecisionEngine decisions = new IdvDecisionEngine();

    public DocumentIaLabService(
            KycProperties properties,
            IntegrationService integrations,
            DocumentAnalysisParser parser,
            SchemaRegistry registry,
            ImageQualityGate qualityGate,
            VisionDocumentPort vision,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.integrations = integrations;
        this.parser = parser;
        this.registry = registry;
        this.qualityGate = qualityGate;
        this.vision = vision;
        this.objectMapper = objectMapper;
    }

    public DocumentIaCatalogResponse catalog() {
        requireEnabled();
        return registry.catalog();
    }

    public DocumentIaAnalysisResponse fixture(String name, String until) {
        requireEnabled();
        parseUntil(until);
        if (name == null || !FIXTURE_NAME.matcher(name).matches()) {
            throw ApiException.notFound("Fixture not found");
        }
        ClassPathResource resource = new ClassPathResource("document-ia/" + name + ".json");
        if (!resource.exists()) {
            throw ApiException.notFound("Fixture not found");
        }
        String json;
        try {
            json = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw ApiException.notFound("Fixture not found");
        }
        long start = System.nanoTime();
        try {
            DocumentParse parsed = parser.parse(json);
            log.info(
                    "document-ia fixture parsed durationMs={} code={} providerCalled=false",
                    (System.nanoTime() - start) / 1_000_000L,
                    parsed.document().classification().code());
            return staged(until, null, null, parsed, false);
        } catch (InvalidModelJsonException e) {
            log.info(
                    "document-ia fixture rejected durationMs={}",
                    (System.nanoTime() - start) / 1_000_000L);
            throw ApiException.unprocessable("invalid_model_json", "Model JSON does not match the document contract");
        }
    }

    public DocumentIaAnalysisResponse analyze(ApiPrincipal principal, String until, MultipartFile file) {
        requireEnabled();
        var integration = integrations.requireInOrg(principal.organizationId(), principal.integrationId());
        if (!integration.isLive()) {
            throw ApiException.forbidden(
                    "sandbox_no_vision", "Document IA lab does not call the vision provider for test keys");
        }
        parseUntil(until);
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("File is required", List.of(new ErrorDetail("file", "required")));
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation("Image could not be read", List.of(new ErrorDetail("file", "invalid")));
        }
        String mediaType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        return run(bytes, mediaType, until);
    }

    public DocumentIaAnalysisResponse decideImage(byte[] bytes, String mediaType) {
        return decideImage(bytes, mediaType, null);
    }

    public DocumentIaAnalysisResponse decideImage(byte[] bytes, String mediaType, String captureSide) {
        if (bytes == null || bytes.length == 0) {
            throw new com.kyc.ports.ProviderUnavailableException("vision_unavailable");
        }
        String type = mediaType == null || mediaType.isBlank() ? "image/jpeg" : mediaType;
        return run(bytes, type, "decide", captureSide);
    }

    private DocumentIaAnalysisResponse run(byte[] bytes, String mediaType, String until) {
        return run(bytes, mediaType, until, null);
    }

    private DocumentIaAnalysisResponse run(byte[] bytes, String mediaType, String until, String captureSide) {
        long start = System.nanoTime();
        DocumentImageQuality quality = qualityGate.assess(bytes);
        log.info(
                "document-ia quality durationMs={} readable={} reason={} providerCalled=false",
                (System.nanoTime() - start) / 1_000_000L,
                quality.readable(),
                quality.reason());
        if (!quality.readable()) {
            return wantsDecide(until) ? recapture(quality) : DocumentIaAnalysisResponse.quality(quality);
        }
        if (!wantsVision(until)) {
            return DocumentIaAnalysisResponse.quality(quality);
        }
        String prompt = VisionPrompt.fromCatalog(registry.catalog().schemas(), captureSide);
        VisionAttempts.Result result = VisionAttempts.call(vision, parser, bytes, mediaType, prompt);
        VisionAttempts.Success success = result.success();
        if (success == null) {
            return visionFailure(quality, result.failureCode(), until);
        }
        try {
            Object rawModel = objectMapper.readTree(success.raw());
            return staged(until, quality, rawModel, success.parsed(), true);
        } catch (IOException e) {
            return visionFailure(quality, "provider_unavailable", until);
        }
    }

    private DocumentIaAnalysisResponse staged(
            String until,
            DocumentImageQuality quality,
            Object rawModel,
            DocumentParse parsed,
            boolean providerCalled) {
        if (!wantsNormalize(until)) {
            return providerCalled
                    ? DocumentIaAnalysisResponse.vision(
                            quality, rawModel, parsed.document(), parsed.schemaVersionId())
                    : DocumentIaAnalysisResponse.parsed(parsed.document(), parsed.schemaVersionId());
        }
        return normalized(until, quality, rawModel, parsed, providerCalled);
    }

    private DocumentIaAnalysisResponse normalized(
            String until,
            DocumentImageQuality quality, Object rawModel, DocumentParse parsed, boolean providerCalled) {
        SchemaEdition edition = registry.edition(parsed.schemaVersionId()).orElse(null);
        if (edition == null) {
            if (!wantsValidate(until)) {
                return DocumentIaAnalysisResponse.normalized(
                        quality,
                        rawModel,
                        parsed.document(),
                        null,
                        List.of(),
                        List.of(),
                        providerCalled);
            }
            return judged(until, quality, rawModel, parsed, List.of(), List.of(), null, providerCalled);
        }
        FieldNormalizer.Result normalization =
                FieldNormalizer.normalize(edition, parsed.document(), LocalDate.now(ZoneOffset.UTC));
        log.info(
                "document-ia normalized code={} fields={} findings={} providerCalled={}",
                parsed.document().classification().code(),
                normalization.fields().size(),
                normalization.validation().size(),
                providerCalled);
        if (!wantsMrz(until)) {
            return DocumentIaAnalysisResponse.normalized(
                    quality,
                    rawModel,
                    parsed.document().withIdentity(normalization.extractedIdentity()),
                    parsed.schemaVersionId(),
                    normalization.fields(),
                    normalization.validation(),
                    providerCalled);
        }
        MrzEngine.Outcome mrz = MrzEngine.read(
                edition, parsed.document(), normalization, LocalDate.now(ZoneOffset.UTC));
        log.info(
                "document-ia mrz format={} status={} providerCalled={}",
                mrz.report().format(),
                mrz.report().status(),
                providerCalled);
        var identified = parsed.document().withIdentity(normalization.extractedIdentity());
        if (!wantsValidate(until)) {
            return DocumentIaAnalysisResponse.mrz(
                    quality,
                    rawModel,
                    identified,
                    parsed.schemaVersionId(),
                    mrz.fields(),
                    mrz.validation(),
                    mrz.report(),
                    providerCalled);
        }
        return judged(
                until,
                quality,
                rawModel,
                new DocumentParse(identified, parsed.schemaVersionId()),
                mrz.fields(),
                mrz.validation(),
                mrz.report(),
                providerCalled);
    }

    private DocumentIaAnalysisResponse judged(
            String until,
            DocumentImageQuality quality,
            Object rawModel,
            DocumentParse parsed,
            List<com.kyc.dto.documentia.NormalizedField> fields,
            List<com.kyc.dto.documentia.ValidationIssue> validation,
            MrzReport mrz,
            boolean providerCalled) {
        SchemaEdition edition = registry.edition(parsed.schemaVersionId()).orElse(null);
        boolean readable = quality == null || quality.readable();
        String captureReason = quality == null ? null : quality.reason();
        DocumentValidator.Prepared prepared = DocumentValidator.prepare(
                edition, parsed.document(), fields, validation, mrz, readable, captureReason);
        DocumentScores scores = ConfidenceScorer.score(
                edition,
                parsed.document(),
                quality,
                fields,
                prepared.validation(),
                mrz == null ? null : mrz.mrzScore());
        prepared = new DocumentValidator.Prepared(
                prepared.validation(),
                prepared.indicators(),
                withBand(prepared.facts(), scores.band()),
                prepared.signals());
        boolean decide = wantsDecide(until);
        DocumentDecision decision = null;
        if (decide) {
            IdvDecisionEngine.VisionVerdict verdict = decisions.decideVision(prepared.signals(), prepared.facts());
            decision = toDecision(verdict);
            log.info(
                    "document-ia decided code={} overallScore={} band={} issue={} decision={} providerCalled={}",
                    parsed.document().classification().code(),
                    scores.overallScore(),
                    scores.band(),
                    decision.issue(),
                    decision.verificationDecision(),
                    providerCalled);
        }
        return DocumentIaAnalysisResponse.judged(
                decide ? "decide" : "validate",
                quality,
                rawModel,
                parsed.document(),
                parsed.schemaVersionId(),
                fields,
                prepared.validation(),
                mrz,
                prepared.indicators(),
                decide ? scores : null,
                decision,
                providerCalled);
    }

    private DocumentIaAnalysisResponse recapture(DocumentImageQuality quality) {
        DocumentValidator.Prepared prepared = DocumentValidator.prepare(
                null, null, List.of(), List.of(), null, false, quality.reason());
        DocumentScores scores = ConfidenceScorer.score(null, null, quality, List.of(), prepared.validation(), null);
        prepared = new DocumentValidator.Prepared(
                prepared.validation(),
                prepared.indicators(),
                withBand(prepared.facts(), scores.band()),
                prepared.signals());
        DocumentDecision decision = toDecision(decisions.decideVision(prepared.signals(), prepared.facts()));
        log.info(
                "document-ia decided code=null overallScore={} band={} issue={} decision={} providerCalled=false",
                scores.overallScore(),
                scores.band(),
                decision.issue(),
                decision.verificationDecision());
        return DocumentIaAnalysisResponse.judged(
                "decide",
                quality,
                null,
                null,
                null,
                null,
                null,
                null,
                prepared.indicators(),
                scores,
                decision,
                false);
    }

    private DocumentIaAnalysisResponse visionFailure(DocumentImageQuality quality, String code, String until) {
        DocumentIaAnalysisResponse failed = "invalid_model_json".equals(code)
                ? DocumentIaAnalysisResponse.invalidModelJson(quality)
                : DocumentIaAnalysisResponse.providerUnavailable(quality);
        if (!wantsDecide(until)) {
            return failed;
        }
        IdvDecisionEngine.Result result = decisions.review(code);
        return failed.withDecision(new DocumentDecision(
                "REVIEW",
                result.decision().name(),
                IdvDecisionEngine.VISION_RULES_VERSION,
                result.reasons()));
    }

    private static IdvDecisionEngine.VisionFacts withBand(IdvDecisionEngine.VisionFacts facts, String band) {
        return new IdvDecisionEngine.VisionFacts(
                facts.readable(),
                facts.captureReason(),
                facts.expired(),
                facts.unknown(),
                facts.missingRequired(),
                facts.mrzFailed(),
                facts.lowOcr(),
                facts.suspicious(),
                band,
                facts.hasError(),
                facts.hasWarning(),
                facts.extractedIdentity());
    }

    private static DocumentDecision toDecision(IdvDecisionEngine.VisionVerdict verdict) {
        String verification = verdict.result().decision() == null ? null : verdict.result().decision().name();
        return new DocumentDecision(
                verdict.issue(), verification, IdvDecisionEngine.VISION_RULES_VERSION, verdict.result().reasons());
    }

    private void requireEnabled() {
        if (!properties.documentIa().labEnabled()) {
            throw ApiException.notFound("Not found");
        }
    }

    private static boolean wantsDecide(String until) {
        return "decide".equals(stage(until));
    }

    private static boolean wantsValidate(String until) {
        String stage = stage(until);
        return "validate".equals(stage) || "decide".equals(stage);
    }

    private static String stage(String until) {
        return until == null ? "" : until.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean wantsMrz(String until) {
        if (until == null || until.isBlank()) {
            return false;
        }
        return switch (until.trim().toLowerCase(Locale.ROOT)) {
            case "mrz", "validate", "decide" -> true;
            default -> false;
        };
    }

    private static boolean wantsNormalize(String until) {
        if (until == null || until.isBlank()) {
            return false;
        }
        return switch (until.trim().toLowerCase(Locale.ROOT)) {
            case "normalize", "mrz", "validate", "decide" -> true;
            default -> false;
        };
    }

    private static boolean wantsVision(String until) {
        if (until == null || until.isBlank()) {
            return true;
        }
        return switch (until.trim().toLowerCase(Locale.ROOT)) {
            case "vision", "normalize", "mrz", "validate", "decide" -> true;
            default -> false;
        };
    }

    static void parseUntil(String until) {
        if (until == null || until.isBlank()) {
            return;
        }
        if (!STAGES.contains(until.trim().toLowerCase(Locale.ROOT))) {
            throw ApiException.validation("Invalid until", List.of(new ErrorDetail("until", "invalid")));
        }
    }
}
