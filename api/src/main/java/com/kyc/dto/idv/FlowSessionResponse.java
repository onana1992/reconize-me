package com.kyc.dto.idv;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlowSessionResponse(
        @JsonProperty("verification_id") UUID verificationId,
        String status,
        @JsonProperty("consent_text_version") String consentTextVersion,
        @JsonProperty("expires_at") Instant expiresAt,
        String next,
        String reason,
        String product) {

    public FlowSessionResponse(
            UUID verificationId,
            String status,
            String consentTextVersion,
            Instant expiresAt,
            String next,
            String reason) {
        this(verificationId, status, consentTextVersion, expiresAt, next, reason, "identity");
    }
}
