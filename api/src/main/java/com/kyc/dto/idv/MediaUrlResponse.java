package com.kyc.dto.idv;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record MediaUrlResponse(String url, @JsonProperty("expires_at") Instant expiresAt) {}
