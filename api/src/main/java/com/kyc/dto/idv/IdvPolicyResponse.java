package com.kyc.dto.idv;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record IdvPolicyResponse(
        String source,
        Integer version,
        @JsonProperty("published_at") Instant publishedAt,
        @JsonProperty("published_by_name") String publishedByName,
        Profile profile,
        List<Document> documents,
        Flow flow,
        Screening screening,
        Bounds bounds) {

    public record Profile(
            @JsonProperty("expired_outcome") String expiredOutcome,
            @JsonProperty("attempts_outcome") String attemptsOutcome,
            @JsonProperty("ocr_review_at") double ocrReviewAt,
            @JsonProperty("liveness_pass_at") double livenessPassAt,
            @JsonProperty("face_match_fail_at") double faceMatchFailAt,
            @JsonProperty("face_match_pass_at") double faceMatchPassAt) {}

    public record Document(
            String code,
            @JsonProperty("document_type") String documentType,
            String country,
            List<String> sides,
            boolean enabled) {}

    public record Flow(
            @JsonProperty("link_ttl_seconds") int linkTtlSeconds,
            @JsonProperty("max_capture_attempts") int maxCaptureAttempts,
            @JsonProperty("max_liveness_attempts") int maxLivenessAttempts,
            @JsonProperty("consent_text_version") String consentTextVersion) {}

    public record Screening(boolean enabled, @JsonProperty("on_match") String onMatch) {}

    public record Bounds(
            @JsonProperty("ocr_review_at") double[] ocrReviewAt,
            @JsonProperty("liveness_pass_at") double[] livenessPassAt,
            @JsonProperty("face_match_fail_at") double[] faceMatchFailAt,
            @JsonProperty("face_match_pass_at") double[] faceMatchPassAt,
            @JsonProperty("link_ttl_seconds") int[] linkTtlSeconds,
            @JsonProperty("max_capture_attempts") int[] maxCaptureAttempts,
            @JsonProperty("max_liveness_attempts") int[] maxLivenessAttempts) {}
}
