package com.kyc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record VerificationResponse(
        UUID id,
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("external_id") String externalId,
        String status,
        @JsonInclude(JsonInclude.Include.NON_NULL) ApplicantDto applicant,
        @JsonProperty("hosted_url") String hostedUrl,
        @JsonProperty("expires_at") Instant expiresAt,
        Map<String, Object> metadata,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {}
