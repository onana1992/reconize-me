package com.kyc.dto.console;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record ApiKeyListItem(
        UUID id,
        @JsonProperty("key_prefix") String keyPrefix,
        @JsonProperty("created_at") Instant createdAt,
        boolean revoked) {}
