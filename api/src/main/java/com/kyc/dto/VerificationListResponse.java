package com.kyc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record VerificationListResponse(List<VerificationResponse> data, @JsonProperty("next_cursor") String nextCursor) {}
