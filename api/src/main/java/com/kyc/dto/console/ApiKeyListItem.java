package com.kyc.dto.console;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiKeyListItem(
        UUID id,
        @JsonProperty("integration_id") UUID integrationId,
        @JsonProperty("key_prefix") String keyPrefix,
        @JsonProperty("created_at") Instant createdAt,
        boolean revoked,
        String key) {}
