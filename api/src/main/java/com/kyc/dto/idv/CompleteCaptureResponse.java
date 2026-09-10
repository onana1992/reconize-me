package com.kyc.dto.idv;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompleteCaptureResponse(
        String status,
        String next,
        boolean accepted,
        Integer attempt,
        @JsonProperty("quality_rejected") Boolean qualityRejected) {}
