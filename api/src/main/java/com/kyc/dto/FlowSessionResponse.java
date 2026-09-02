package com.kyc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record FlowSessionResponse(
        @JsonProperty("verification_id") UUID verificationId,
        String status,
        @JsonProperty("consent_text_version") String consentTextVersion,
        @JsonProperty("expires_at") Instant expiresAt) {}
