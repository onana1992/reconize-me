package com.kyc.dto.idv;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerificationResponse(
        UUID id,
        String status,
        @JsonProperty("hosted_url") String hostedUrl,
        @JsonProperty("expires_at") Instant expiresAt,
        Applicant applicant,
        Map<String, Object> metadata,
        String decision,
        @JsonProperty("decision_reasons") List<String> decisionReasons,
        List<Signal> signals,
        @JsonProperty("extracted_identity") Map<String, Object> extractedIdentity,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public record Applicant(
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String email) {}

    public record Signal(String code, String outcome, Double score) {}
}
