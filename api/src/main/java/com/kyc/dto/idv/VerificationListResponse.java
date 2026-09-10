package com.kyc.dto.idv;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record VerificationListResponse(List<VerificationResponse> items, @JsonProperty("next_cursor") String nextCursor) {}
