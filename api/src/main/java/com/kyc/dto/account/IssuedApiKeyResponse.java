package com.kyc.dto.account;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IssuedApiKeyResponse(
        UUID id,
        String key,
        @JsonProperty("key_prefix") String keyPrefix,
        @JsonProperty("integration_id") UUID integrationId) {}
