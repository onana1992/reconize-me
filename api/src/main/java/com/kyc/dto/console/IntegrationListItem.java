package com.kyc.dto.console;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record IntegrationListItem(
        UUID id,
        String product,
        String mode,
        String name,
        @JsonProperty("created_at") Instant createdAt) {}
