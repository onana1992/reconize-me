package com.kyc.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnrollmentResponse(
        UUID id,
        String status,
        String source,
        @JsonProperty("external_id") String externalId,
        @JsonProperty("source_verification_id") UUID sourceVerificationId,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("ended_at") Instant endedAt) {

    public static EnrollmentResponse absent() {
        return new EnrollmentResponse(null, "absent", null, null, null, null, null);
    }
}
