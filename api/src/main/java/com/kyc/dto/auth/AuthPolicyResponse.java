package com.kyc.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthPolicyResponse(
        String version,
        @JsonProperty("approval_threshold") BigDecimal approvalThreshold,
        @JsonProperty("review_threshold") BigDecimal reviewThreshold,
        @JsonProperty("on_borderline") String onBorderline,
        @JsonProperty("max_capture_attempts") int maxCaptureAttempts,
        @JsonProperty("liveness_required") boolean livenessRequired,
        boolean published,
        boolean active,
        @JsonProperty("published_at") Instant publishedAt,
        @JsonProperty("published_by_name") String publishedByName) {}
