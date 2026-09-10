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
        String next) {}
