package com.kyc.dto.idv;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record PublishIdvPolicyRequest(
        Profile profile,
        @JsonProperty("document_codes") List<String> documentCodes,
        Flow flow,
        Screening screening,
        @JsonProperty("integration_id") UUID integrationId) {

    public record Profile(
            @JsonProperty("expired_outcome") String expiredOutcome,
            @JsonProperty("attempts_outcome") String attemptsOutcome,
            @JsonProperty("ocr_review_at") Double ocrReviewAt,
            @JsonProperty("liveness_pass_at") Double livenessPassAt,
            @JsonProperty("face_match_fail_at") Double faceMatchFailAt,
            @JsonProperty("face_match_pass_at") Double faceMatchPassAt) {}

    public record Flow(
            @JsonProperty("link_ttl_seconds") Integer linkTtlSeconds,
            @JsonProperty("max_capture_attempts") Integer maxCaptureAttempts,
            @JsonProperty("max_liveness_attempts") Integer maxLivenessAttempts) {}

    public record Screening(Boolean enabled, @JsonProperty("on_match") String onMatch) {}
}
