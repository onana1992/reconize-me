package com.kyc.dto.idv;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompleteCaptureResponse(
        String status,
        String next,
        boolean accepted,
        Integer attempt,
        @JsonProperty("quality_rejected") Boolean qualityRejected,
        String reason,
        List<String> reasons,
        @JsonProperty("match_score") BigDecimal matchScore,
        @JsonProperty("failure_reason") String failureReason) {

    public CompleteCaptureResponse(
            String status,
            String next,
            boolean accepted,
            Integer attempt,
            Boolean qualityRejected,
            String reason) {
        this(status, next, accepted, attempt, qualityRejected, reason, null, null, null);
    }
}
