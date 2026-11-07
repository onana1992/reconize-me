package com.kyc.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.adapters.StubDocumentAuthenticity;
import com.kyc.entities.VerificationAuthenticityAssessment;
import com.kyc.entities.VerificationAuthenticityAssessmentKey;
import com.kyc.repositories.VerificationAuthenticityAssessmentRepository;
import com.kyc.services.IdvDecisionEngine.AlertScore;
import com.kyc.services.IdvDecisionEngine.AuthenticityFacts;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Une ligne par face. Une nouvelle analyse remplace la précédente. */
@Service
public class AuthenticityAssessmentStore {

    public static final String ENGINE_UNAVAILABLE = "unavailable";

    private final VerificationAuthenticityAssessmentRepository assessments;
    private final ObjectMapper objectMapper;

    public AuthenticityAssessmentStore(
            VerificationAuthenticityAssessmentRepository assessments, ObjectMapper objectMapper) {
        this.assessments = assessments;
        this.objectMapper = objectMapper;
    }

    public Optional<VerificationAuthenticityAssessment> find(UUID verificationId, String side) {
        return assessments.findByVerificationIdAndSide(verificationId, side);
    }

    public List<VerificationAuthenticityAssessment> findAll(UUID verificationId) {
        return assessments.findByVerificationIdOrderBySideAsc(verificationId);
    }

    public void replaceMeasured(UUID verificationId, String side, JsonNode assessment, long durationMs, Instant now) {
        replace(
                verificationId,
                side,
                text(assessment, "engine_version", StubDocumentAuthenticity.ENGINE_VERSION),
                text(assessment, "fusion_version", StubDocumentAuthenticity.ENGINE_VERSION),
                assessment.path("forensic_usable").asBoolean(false),
                number(assessment, "authenticity_score"),
                number(assessment, "tampering_score"),
                number(assessment, "presentation_score"),
                json(assessment.get("alerts")),
                durationMs,
                now);
    }

    public void replaceUnavailable(UUID verificationId, String side, long durationMs, Instant now) {
        replace(
                verificationId,
                side,
                ENGINE_UNAVAILABLE,
                ENGINE_UNAVAILABLE,
                false,
                null,
                null,
                null,
                null,
                durationMs,
                now);
    }

    public AuthenticityFacts facts(VerificationAuthenticityAssessment row) {
        List<AlertScore> alerts = new ArrayList<>();
        if (row.getAlertsJson() != null && !row.getAlertsJson().isBlank()) {
            try {
                JsonNode tree = objectMapper.readTree(row.getAlertsJson());
                if (tree.isArray()) {
                    for (JsonNode alert : tree) {
                        JsonNode score = alert.get("score");
                        Double value = score == null || score.isNull() || !score.isNumber() ? null : score.asDouble();
                        alerts.add(new AlertScore(alert.path("code").asText(), value));
                    }
                }
            } catch (JsonProcessingException e) {
                alerts = List.of();
            }
        }
        return new AuthenticityFacts(row.isForensicUsable(), row.getAuthenticityScore(), List.copyOf(alerts));
    }

    private void replace(
            UUID verificationId,
            String side,
            String engineVersion,
            String fusionVersion,
            boolean forensicUsable,
            Double authenticityScore,
            Double tamperingScore,
            Double presentationScore,
            String alertsJson,
            long durationMs,
            Instant now) {
        VerificationAuthenticityAssessmentKey key = new VerificationAuthenticityAssessmentKey(verificationId, side);
        if (assessments.existsById(key)) {
            assessments.deleteById(key);
            assessments.flush();
        }
        assessments.save(new VerificationAuthenticityAssessment(
                verificationId,
                side,
                engineVersion,
                fusionVersion,
                forensicUsable,
                authenticityScore,
                tamperingScore,
                presentationScore,
                alertsJson,
                durationMs,
                now));
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            return fallback;
        }
        return value.asText();
    }

    private static Double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asDouble();
    }

    private String json(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
