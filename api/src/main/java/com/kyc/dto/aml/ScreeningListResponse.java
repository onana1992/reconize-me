package com.kyc.dto.aml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ScreeningListResponse(List<ScreeningResponse> data, @JsonProperty("next_cursor") String nextCursor) {}
