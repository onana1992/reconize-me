package com.kyc.dto.aml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublishAmlPolicyRequest(
        @JsonProperty("review_threshold") BigDecimal reviewThreshold,
        @JsonProperty("confirm_threshold") BigDecimal confirmThreshold,
        List<String> categories,
        @JsonProperty("attach_to_identity") boolean attachToIdentity,
        @JsonProperty("on_match") String onMatch,
        @JsonProperty("test_integration_id") UUID testIntegrationId,
        @JsonProperty("live_integration_id") UUID liveIntegrationId,
        @JsonProperty("integration_id") UUID integrationId) {}
