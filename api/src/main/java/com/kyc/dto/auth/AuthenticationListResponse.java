package com.kyc.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AuthenticationListResponse(
        List<AuthenticationResponse> data, @JsonProperty("next_cursor") String nextCursor) {}
