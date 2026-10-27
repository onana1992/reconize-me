package com.kyc.dto.aml;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AmlPolicyResponse(
        String version,
        @JsonProperty("review_threshold") BigDecimal reviewThreshold,
        @JsonProperty("confirm_threshold") BigDecimal confirmThreshold,
        List<String> categories,
        @JsonProperty("attach_to_identity") boolean attachToIdentity,
        @JsonProperty("on_match") String onMatch,
        @JsonProperty("test_integration_id") UUID testIntegrationId,
        @JsonProperty("live_integration_id") UUID liveIntegrationId,
        boolean published,
        boolean active,
        @JsonProperty("published_at") Instant publishedAt,
        @JsonProperty("published_by_name") String publishedByName) {}
