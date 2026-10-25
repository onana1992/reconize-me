package com.kyc.services.aml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.entities.Integration;
import com.kyc.entities.Verification;
import com.kyc.enums.AmlOnMatch;
import com.kyc.enums.ProductCode;
import com.kyc.enums.ScreeningOutcome;
import com.kyc.enums.VerificationDecision;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.services.ScreeningService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AmlAttachmentService {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final AmlPolicyService policies;
    private final ScreeningService screenings;
    private final IntegrationRepository integrations;
    private final ObjectMapper objectMapper;

    public AmlAttachmentService(
            AmlPolicyService policies,
            ScreeningService screenings,
            IntegrationRepository integrations,
            ObjectMapper objectMapper) {
        this.policies = policies;
        this.screenings = screenings;
        this.integrations = integrations;
        this.objectMapper = objectMapper;
    }

    public void attach(Verification verification, Integration identity, Instant now) {
        AmlPolicy policy = policies.effective(verification.getOrganizationId());
        if (!policy.attachToIdentity()) {
            return;
        }
        if (identity.isLive()) {
            unavailable(verification, now);
            return;
        }
        if (policy.onMatch() != AmlOnMatch.IGNORE && policy.testIntegrationId() == null) {
            unavailable(verification, now);
            return;
        }
        if (policy.testIntegrationId() == null) {
            return;
        }
        Integration aml = integrations
                .findByIdAndOrganizationId(policy.testIntegrationId(), verification.getOrganizationId())
                .orElse(null);
        if (aml == null || aml.getProduct() != ProductCode.AML || aml.isLive()) {
            if (policy.onMatch() != AmlOnMatch.IGNORE) {
                unavailable(verification, now);
            }
            return;
        }
        Map<String, Object> extracted = readMap(verification.getExtractedIdentity());
        String first = text(extracted, "first_name");
        String last = text(extracted, "last_name");
        if (first == null || last == null) {
            if (policy.onMatch() != AmlOnMatch.IGNORE) {
                unavailable(verification, now);
            }
            return;
        }
        ScreeningService.ParsedRequest parsed;
        try {
            var subject = SubjectParser.parse(
                    first,
                    text(extracted, "middle_name"),
                    last,
                    text(extracted, "birth_date"),
                    text(extracted, "document_country"),
                    null,
                    text(extracted, "document_number"),
                    idType(text(extracted, "document_type")));
            parsed = new ScreeningService.ParsedRequest(
                    subject.input(), subject.countryOfResidence(), subject.idType(), null, verification.getId());
        } catch (SubjectParser.SubjectException ex) {
            if (policy.onMatch() != AmlOnMatch.IGNORE) {
                unavailable(verification, now);
            }
            return;
        }
        var screening = screenings.createForVerification(aml, verification.getId(), parsed);
        if (policy.onMatch() == AmlOnMatch.IGNORE || screening.getOutcome() == ScreeningOutcome.CLEAR) {
            verification.linkAml(screening.getId(), null, null, now);
            return;
        }
        String reason = screening.getOutcome() == ScreeningOutcome.CONFIRMED_MATCH
                ? "aml_confirmed_match"
                : "aml_potential_match";
        VerificationDecision decision = screening.getOutcome() == ScreeningOutcome.CONFIRMED_MATCH
                        && policy.onMatch() == AmlOnMatch.BLOCK
                ? VerificationDecision.DECLINED
                : VerificationDecision.REVIEW;
        verification.linkAml(screening.getId(), decision, write(append(verification.getDecisionReasons(), reason)), now);
    }

    private void unavailable(Verification verification, Instant now) {
        verification.linkAml(
                null,
                VerificationDecision.REVIEW,
                write(append(verification.getDecisionReasons(), "aml_unavailable")),
                now);
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
