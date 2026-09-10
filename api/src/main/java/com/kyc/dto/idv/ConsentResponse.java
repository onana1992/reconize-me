package com.kyc.dto.idv;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ConsentResponse(String status, String next, @JsonProperty("recorded_at") Instant recordedAt) {}
