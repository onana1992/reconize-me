package com.kyc.dto.console;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntegrationResponse(
        UUID id,
        String product,
        String mode,
        String name,
        @JsonProperty("created_at") Instant createdAt,
        List<ApiKeyListItem> keys,
        String key) {}
