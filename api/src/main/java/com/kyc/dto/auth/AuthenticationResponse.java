package com.kyc.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthenticationResponse(
        UUID id,
        @JsonProperty("integration_id") UUID integrationId,
        @JsonProperty("integration_mode") String integrationMode,
        @JsonProperty("external_id") String externalId,
        @JsonProperty("enrollment_id") UUID enrollmentId,
        String status,
        List<String> reasons,
        @JsonProperty("failure_reason") String failureReason,
        @JsonProperty("match_score") BigDecimal matchScore,
        String engine,
        @JsonProperty("policy_version") String policyVersion,
        Map<String, Object> metadata,
        @JsonProperty("consent_version") String consentVersion,
        @JsonProperty("hosted_url") String hostedUrl,
        @JsonProperty("expires_at") Instant expiresAt,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("decided_at") Instant decidedAt) {}
