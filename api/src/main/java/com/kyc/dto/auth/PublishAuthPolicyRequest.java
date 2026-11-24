package com.kyc.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record PublishAuthPolicyRequest(
        @JsonProperty("integration_id") UUID integrationId,
        @JsonProperty("approval_threshold") BigDecimal approvalThreshold,
        @JsonProperty("review_threshold") BigDecimal reviewThreshold,
        @JsonProperty("on_borderline") String onBorderline,
        @JsonProperty("max_capture_attempts") Integer maxCaptureAttempts) {}
