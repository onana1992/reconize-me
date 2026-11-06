package com.kyc.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.kyc.adapters.AuthenticityUnavailable;
import com.kyc.config.KycProperties;
import com.kyc.dto.antispoof.AntiSpoofLabResponse;
import com.kyc.ports.DocumentAuthenticityPort;
import com.kyc.security.ApiPrincipal;
import com.kyc.services.IdvDecisionEngine.AlertScore;
import com.kyc.services.IdvDecisionEngine.AuthenticityFacts;
import com.kyc.services.IdvDecisionEngine.AuthenticityPolicy;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AntiSpoofLabService {

    private static final Logger log = LoggerFactory.getLogger(AntiSpoofLabService.class);

    private final KycProperties properties;
    private final IntegrationService integrations;
    private final DocumentAuthenticityPort authenticity;
    private final IdvDecisionEngine decisions = new IdvDecisionEngine();

    public AntiSpoofLabService(
            KycProperties properties, IntegrationService integrations, DocumentAuthenticityPort authenticity) {
        this.properties = properties;
        this.integrations = integrations;
        this.authenticity = authenticity;
    }

    public ResponseEntity<AntiSpoofLabResponse> analyze(ApiPrincipal principal, MultipartFile file) {
        if (!properties.antiSpoof().labEnabled()) {
            throw ApiException.notFound("Not found");
        }
        var integration = integrations.requireInOrg(principal.organizationId(), principal.integrationId());
        if (!integration.isLive()) {
            throw ApiException.forbidden("sandbox_no_anti_spoof", "Sandbox keys cannot call the anti-spoof service");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("file is required", List.of(new ErrorDetail("file", "required")));
        }
        byte[] jpeg = read(file);
        long started = System.nanoTime();
        try {
            JsonNode assessment = authenticity.analyze(jpeg);
            String engineVersion = assessment.path("engine_version").asText();
            journal(started, engineVersion, assessment, null, null);
            return ResponseEntity.ok(
                    AntiSpoofLabResponse.assessed(engineVersion, assessment, decisions.applyAuthenticity(facts(assessment), policy())));
        } catch (AuthenticityUnavailable ex) {
            journal(started, null, null, "authenticity_unavailable", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(AntiSpoofLabResponse.unavailable(decisions.authenticityUnavailable()));
        }
    }

    private AuthenticityPolicy policy() {
        var config = properties.antiSpoof();
        return new AuthenticityPolicy(config.reviewAt(), config.declineAt(), config.openDeclineCodes());
    }

    private static AuthenticityFacts facts(JsonNode assessment) {
        List<AlertScore> alerts = new ArrayList<>();
        for (JsonNode alert : assessment.path("alerts")) {
            JsonNode score = alert.get("score");
            Double value = score == null || score.isNull() || !score.isNumber() ? null : score.asDouble();
            alerts.add(new AlertScore(alert.path("code").asText(), value));
        }
        JsonNode authenticity = assessment.get("authenticity_score");
        Double authenticityScore =
                authenticity == null || authenticity.isNull() || !authenticity.isNumber() ? null : authenticity.asDouble();
        return new AuthenticityFacts(assessment.path("forensic_usable").asBoolean(false), authenticityScore, alerts);
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw ApiException.validation("file is required", List.of(new ErrorDetail("file", "required")));
        }
    }

    private static void journal(
            long started, String engineVersion, JsonNode assessment, String error, String kind) {
        long durationMs = (System.nanoTime() - started) / 1_000_000L;
        log.info(
                "anti-spoof lab durationMs={} engineVersion={} splicing={} text_alteration={} compression_inconsistency={} screen_replay={} printed_copy={} error={} kind={}",
                durationMs,
                engineVersion,
                score(assessment, "splicing"),
                score(assessment, "text_alteration"),
                score(assessment, "compression_inconsistency"),
                score(assessment, "screen_replay"),
                score(assessment, "printed_copy"),
                error,
                kind);
    }

    private static String score(JsonNode assessment, String code) {
        if (assessment == null) {
            return "null";
        }
        for (JsonNode alert : assessment.path("alerts")) {
            if (code.equals(alert.path("code").asText())) {
                JsonNode value = alert.get("score");
                if (value == null || value.isNull() || !value.isNumber()) {
                    return "null";
                }
                return String.format(Locale.ROOT, "%.3f", value.asDouble());
            }
        }
        return "null";
    }
}
