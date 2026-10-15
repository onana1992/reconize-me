package com.kyc.dto.idv;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record LivenessSessionResponse(
        @JsonProperty("session_id") String sessionId,
        String region,
        Credentials credentials) {

    public record Credentials(
            @JsonProperty("access_key_id") String accessKeyId,
            @JsonProperty("secret_access_key") String secretAccessKey,
            @JsonProperty("session_token") String sessionToken,
            Instant expiration) {}
}
