package com.kyc.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record InvitePreviewResponse(
        String email, String role, @JsonProperty("expires_at") Instant expiresAt) {}
